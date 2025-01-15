package uk.co.compendiumdev.thingifier.core.domain.definitions.field.definition;

import uk.co.compendiumdev.thingifier.core.reporting.ValidationReport;
import uk.co.compendiumdev.thingifier.core.domain.definitions.DefinedFields;
import uk.co.compendiumdev.thingifier.core.domain.randomdata.RandomString;
import uk.co.compendiumdev.thingifier.core.domain.definitions.field.instance.FieldValue;
import uk.co.compendiumdev.thingifier.core.domain.definitions.validation.ValidationRule;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;

// todo: beginning to think that we should have an XField for each field type
// e.g. IdField, StringField, etc. - possibly with an interface or abstract
//      AField class - e.g. for 'mandatory'
//      then we would not have 'maximumStringLength' 'maximumIntegerValue' etc.
//      would have 'maximum' 'minimum' methods - some fields would have unique methods
public final class Field {

    private final String name;
    private final FieldType type;
    private final Set<String> fieldExamples;
    private boolean fieldIsOptional;

    // default value for the field
    private String defaultValue;
    private final List<ValidationRule> validationRules;
    private boolean truncateStringIfTooLong;

    private int maximumIntegerValue;
    private int minimumIntegerValue;
    private final boolean allowedNullable;
    // todo: use BigDecimal for the internal float representations
    private float maximumFloatValue;
    private float minimumFloatValue;
    private DefinedFields objectDefinition;

    private boolean mustBeUnique;

    private int truncatedStringLength;
    private Function<String, String> transformToMakeUnique;

    // todo: rather than all these fields, consider moving to more validation rules
    // to help keep the class to a more manageable size or create a FieldValidator class

    public Field(final String name, final FieldType type) {
        this.name = name;
        this.type = type;
        validationRules = new ArrayList<>();

        fieldIsOptional = true;
        if(type == FieldType.AUTO_INCREMENT || type == FieldType.AUTO_GUID){
            fieldIsOptional = false;
        }

        truncateStringIfTooLong=false;
        truncatedStringLength=-1;
        fieldExamples = new HashSet<>();
        maximumIntegerValue = Integer.MAX_VALUE;
        minimumIntegerValue = Integer.MIN_VALUE;
        maximumFloatValue = Float.MAX_VALUE;
        minimumFloatValue = Float.MIN_VALUE;
        mustBeUnique = false;
        allowedNullable=false;

        transformToMakeUnique = (s) -> s;
    }

    public static Field is(String name, FieldType type) {
        return new Field(name, type);
    }

    public String getName() {
        return name;
    }


    public Field withDefaultValue(String aDefaultValue) {
        this.defaultValue = aDefaultValue;
        fieldExamples.add(aDefaultValue);
        return this;
    }

    public FieldValue getDefaultValue() {
        // todo: allow configuration of allowedNullable
        // todo: handle defaults of object and array
        if(defaultValue==null && !allowedNullable){
            // get the definition default
            return FieldValue.is(this, type.getDefault());
        }
        return FieldValue.is(this, defaultValue);
    }

    public boolean hasDefaultValue() {
        return defaultValue != null;
    }

    public Field setMustBeUnique(boolean uniqueness) {
        this.mustBeUnique = uniqueness;
        return this;
    }

    public boolean mustBeUnique(){
        return this.mustBeUnique;
    }

    public FieldType getType() {
        return type;
    }

    public Field withValidation(ValidationRule validationRule) {
        validationRules.add(validationRule);
        return this;
    }

    public Field withValidation(ValidationRule... validationRule) {
        validationRules.addAll(Arrays.asList(validationRule));
        return this;
    }

    // TODO: when all field values use field, then this should move to the value and out of the field
    public ValidationReport validate(FieldValue value) {
        boolean NOT_ALLOWED_TO_SET_IDs = false;
        return validate(value, NOT_ALLOWED_TO_SET_IDs);
    }

    // allowedToSetIds is a bit of hack - refactor other code so not required
    public ValidationReport validate(FieldValue value, boolean allowedToSetIds) {


        ValidationReport report = new ValidationReport();

        // missing fields will come through as null,
        // if they are optional then that is fine
        if (fieldIsOptional && value == null) {
            report.setValid(true);
            return report;
        }

        if(!allowedToSetIds) {
            if (type == FieldType.AUTO_INCREMENT) {
                report.setValid(false);
                report.addErrorMessage(String.format("%s : field is an ID, you can't set it", this.getName()));
                return report;
            }
        }

        if (!fieldIsOptional && value == null) {
            report.setValid(false);
            report.addErrorMessage(String.format("%s : field is mandatory", this.getName()));
            return report;
        }

        // always validate against type
        //if(shouldValidateValuesAgainstType) {

            if (type == FieldType.BOOLEAN) {
                validateBooleanValue(value, report);
            }

            if (type == FieldType.INTEGER) {
                validateIntegerValue(value, report);
            }

            if(type == FieldType.STRING){
                // length is validated by a rule
            }

            if (type == FieldType.FLOAT) {
                validateFloatValue(value, report);
            }

            if (type == FieldType.ENUM) {
                validateEnumValue(value, report);
            }

            // TODO : add validation for DATE

            if(type == FieldType.OBJECT){
                validateObjectValue(value, report);
            }


        //}

        for (ValidationRule rule : validationRules) {
            if (!rule.validates(value)) {
                report.setValid(false);
                report.addErrorMessage(rule.getErrorMessage(value));
            }
        }

        return report;
    }

    private void validateObjectValue(final FieldValue value, final ValidationReport report) {
        if(value!= null && value.asObject()!=null){
            final ValidationReport objectValidity =
                    value.asObject().
                            validateFields(new ArrayList<>(), true);
            report.combine(objectValidity);
        }
    }

    private void validateEnumValue(final FieldValue value, final ValidationReport report) {
        if (!getExamples().contains(value.asString())) {
            reportThisValueDoesNotMatchType(report, value.asString(), getExamples());
        }
    }

    private void validateFloatValue(final FieldValue value, final ValidationReport report) {
        try {
            float floatValue = value.asFloat();
            if (!withinAllowedFloatRange(floatValue)) {
                report.setValid(false);
                report.addErrorMessage(
                        String.format(
                                "%s : %s is not within range for type %s (%f to %f)",
                                this.getName(), value.asString(),
                                type, minimumFloatValue, maximumFloatValue));
            }

        } catch (NumberFormatException e) {
            reportThisValueDoesNotMatchType(report, value.asString());
        }
    }

    private void validateIntegerValue(final FieldValue value,
                                      final ValidationReport report) {
        try {

            // integers can come in from JSON as doubles
            BigDecimal intFloatValue = new BigDecimal(value.asString());

            BigDecimal fractionalPart = intFloatValue.abs().subtract(new BigDecimal(intFloatValue.abs().toBigInteger()));

            if(!(fractionalPart.equals(new BigDecimal("0")) || fractionalPart.equals(new BigDecimal("0.0")))){
                throw new NumberFormatException();
            }

            int intVal = intFloatValue.intValue();

            if (!withinAllowedIntegerRange(intVal)) {
                report.setValid(false);
                report.addErrorMessage(
                        String.format(
                                "%s : %d is not within range for type %s (%d to %d)",
                                this.getName(), intVal,
                                type, minimumIntegerValue, maximumIntegerValue));
            }
        } catch (NumberFormatException e) {
            reportThisValueDoesNotMatchType(report, value.asString());

        }
    }

    private void reportThisValueDoesNotMatchType(final ValidationReport report,
                                                 final String valueString) {
        reportThisValueDoesNotMatchType(report, valueString, List.of());
    }

    private void reportThisValueDoesNotMatchType(final ValidationReport report,
                                                 final String valueString,
                                                 final List<String> validValues) {

        String reportValids = "";

        if(validValues!=null && !validValues.isEmpty()){
            reportValids = " - valid values are [%s]".formatted(String.join(",",validValues));
        }

        report.setValid(false);
        report.addErrorMessage(
                String.format(
                        "%s : %s does not match type %s%s",
                        name,  valueString, type, reportValids));
    }

    private void validateBooleanValue(final FieldValue value,
                                      final ValidationReport report) {
        try{
            value.asBoolean();
        }catch(IllegalArgumentException e){
            report.setValid(false);
            report.addErrorMessage(
                    String.format(
                            "%s : %s does not match type %s (true, false)",
                            this.getName(),
                            value.asString(), type));
        }
    }


    public List<ValidationRule> validationRules() {
        return validationRules;
    }

    public boolean isMandatory(){
        return !fieldIsOptional;
    }

    public Field makeMandatory() {
        fieldIsOptional = false;
        return this;
    }

    /*
       todo: consider adding Formatting Rules
        instead of truncateString To -
        could be useful for integers, float rounding, dates, etc.
     */
    public Field truncateStringTo(final int maximumTruncatedLengthOfString) {
        truncateStringIfTooLong = true;
        truncatedStringLength =maximumTruncatedLengthOfString;
        return this;
    }

    public boolean shouldTruncate() {
        return truncateStringIfTooLong;
    }

    public String getAsTruncatedString(FieldValue value){
        String truncated = value.asString();
        if(truncateStringIfTooLong && truncatedStringLength>-1){
            if(truncated.length()>truncatedStringLength) {
                truncated = truncated.substring(0, truncatedStringLength);
            }
        }
        return truncated;
    }

    public Field withExample(final String anExample) {
        fieldExamples.add(anExample);
        return this;
    }

    public List<String> getExamples() {

        // field might have examples in definition, if it does, use them
        if(!fieldExamples.isEmpty()){
            return new ArrayList<>(fieldExamples);
        }

        Set<String> buildExamples = new HashSet<>();

        if (type == FieldType.BOOLEAN) {
            String[] samples = {"true", "false"};
            buildExamples.addAll(Arrays.asList(samples));
        }

        if(type==FieldType.INTEGER){
            int rndInt = ThreadLocalRandom.current().
                            nextInt(minimumIntegerValue, maximumIntegerValue + 1);
            buildExamples.add(String.valueOf(rndInt));
        }

        if(type==FieldType.AUTO_INCREMENT){
            int rndInt = ThreadLocalRandom.current().
                    nextInt(1, 100);
            buildExamples.add(String.valueOf(rndInt));
        }

        if(type==FieldType.AUTO_GUID){
            buildExamples.add(UUID.randomUUID().toString());
        }

        if(type==FieldType.FLOAT){
            final float rndFloat = minimumFloatValue + ThreadLocalRandom.current().nextFloat() * (maximumFloatValue - minimumFloatValue);
            buildExamples.add(String.valueOf(rndFloat));
        }

        // TODO: try to use regex in matching rules to generate
        if(type==FieldType.STRING){
            if(fieldExamples.isEmpty()){
                buildExamples.add(
                    getAsTruncatedString(
                        valueFor(new RandomString().get(20))
                    )
                );
            }
        }

        // return as a list
        return new ArrayList<>(buildExamples);
    }

    public String getRandomExampleValue() {
        final List<String> examples = getExamples();

        if(examples.isEmpty()){
            return "";
        }

        return examples.get(new Random().nextInt(examples.size()));
    }

    public Field withMaximumValue(final int maximumInteger) {
        this.maximumIntegerValue = maximumInteger;
        return this;
    }

    public Field withMinimumValue(final int minimumInteger) {
        this.minimumIntegerValue = minimumInteger;
        return this;
    }

    public boolean withinAllowedIntegerRange(final int intVal) {
        return (intVal>=minimumIntegerValue &&
                intVal<=maximumIntegerValue);
    }

    public Field withMaximumValue(final float maxFloat) {
        this.maximumFloatValue = maxFloat;
        return this;
    }

    public Field withMinimumValue(final float minFloat) {
        this.minimumFloatValue = minFloat;
        return this;
    }

    public boolean withinAllowedFloatRange(final float floatValue) {
        return (floatValue>=minimumFloatValue &&
                floatValue<=maximumFloatValue);
    }

    public int getMaximumIntegerValue() {
        return maximumIntegerValue;
    }

    public int getMinimumIntegerValue() {
        return minimumIntegerValue;
    }

    public Float getMinimumFloatValue() {
        return minimumFloatValue;
    }

    public Float getMaximumFloatValue() {
        return maximumFloatValue;
    }

    public Field withField(final Field childField) {
        if(objectDefinition==null){
            objectDefinition = new DefinedFields();
        }
        objectDefinition.addField(childField);
        return this;
    }

    public DefinedFields getObjectDefinition() {
        return objectDefinition;
    }

    public String getActualValueToAdd(final FieldValue value) {

        switch (type){
            case BOOLEAN:
                return Boolean.valueOf(value.asString()).toString();
            case FLOAT:
                return Float.valueOf(value.asString()).toString();
            case STRING:
                if(shouldTruncate()){
                    return getAsTruncatedString(value);
                }else{
                    return value.asString();
                }
            case INTEGER:
            case AUTO_INCREMENT:
                Double dVal = Double.parseDouble(value.asString());
                return String.valueOf(dVal.intValue());
            case AUTO_GUID:
            case OBJECT:
            case ENUM:
            case DATE:
                return value.asString();
            default:
                System.out.println("Unhandled value to add on set");
                return value.asString();
        }
    }

    public FieldValue valueFor(String value) {
        return FieldValue.is(this, value);
    }

    public Field setUniqueAfterTransform(Function<String, String> transform) {
        setMustBeUnique(true);
        transformToMakeUnique = transform;
        return this;
    }

    public String uniqueAfterTransform(String string) {
        try {
            return transformToMakeUnique.apply(string);
        }catch (Exception e){
            return "ERROR: " + string + " " + e.getMessage();
        }
    }
}
