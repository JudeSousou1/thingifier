package uk.co.compendiumdev.thingifier.core;

import uk.co.compendiumdev.thingifier.core.domain.definitions.Cardinality;
import uk.co.compendiumdev.thingifier.core.domain.definitions.ERSchema;
import uk.co.compendiumdev.thingifier.core.domain.definitions.EntityDefinition;
import uk.co.compendiumdev.thingifier.core.domain.definitions.relationship.RelationshipDefinition;
import uk.co.compendiumdev.thingifier.core.domain.instances.ERInstanceData;
import uk.co.compendiumdev.thingifier.core.domain.instances.EntityInstance;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/*
    The ERM has the 'model' (ERSchema) and the 'instances' (things).
    Schema and instances are separate to allow us to have multiple
    'databases' in memory at the same time built from the same schema.
 */
public class EntityRelModel {

    public static final String DEFAULT_DATABASE_NAME = "__default";

    // a Map so that key, database can be used
    // e.g. key from a 'session', or 'custom' or 'default'
    private final Map<String, ERInstanceData> databases;
    private final ERSchema schema; // all the definitions

    public EntityRelModel(){
        schema = new ERSchema();
        databases = new HashMap<String,ERInstanceData>();
        databases.put(DEFAULT_DATABASE_NAME, new ERInstanceData());
    }

    public EntityRelModel(final ERSchema schema, final ERInstanceData erInstanceData) {
        this.schema = schema;
        this.databases = new HashMap<String, ERInstanceData>();
        this.databases.put(DEFAULT_DATABASE_NAME,erInstanceData);
    }

    public EntityDefinition createEntityDefinition(final String entityName, final String pluralName) {
        EntityDefinition defn = schema.defineEntity(entityName, pluralName);
        for(ERInstanceData database : databases.values()){
            database.createInstanceCollectionFor(defn);
        }
        return defn;
    }

    public ERSchema getSchema(){
        return schema;
    }

    // TODO: use of this is basically deprecated sine is refers to the default database
    @Deprecated() // we should use the parameterised version
    public ERInstanceData getInstanceData(){
        return databases.get(DEFAULT_DATABASE_NAME);
    }

    public ERInstanceData getInstanceData(String databaseKey) {
        return databases.get(databaseKey);
    }

    // ERM Object Level
    public EntityRelModel cloneWithDifferentData(final List<EntityInstance> instances) {
        return new EntityRelModel(schema, new ERInstanceData(instances));
    }

    // Schema methods
    // TODO: consider inlining all of these
    public boolean hasEntityNamed(final String aName) {
        return schema.hasEntityNamed(aName);
    }

    public List<String> getEntityNames() {
        return schema.getEntityNames();
    }

    public Collection<RelationshipDefinition> getRelationshipDefinitions() {
        return schema.getRelationships();
    }

    public RelationshipDefinition createRelationshipDefinition(
            EntityDefinition from, EntityDefinition to, final String named, final Cardinality of) {
        return schema.defineRelationship(from, to, named, of);
    }

    public boolean hasRelationshipNamed(final String relationshipName) {
        return schema.hasRelationshipNamed(relationshipName);
    }

    public boolean hasEntityWithPluralNamed(final String term) {
        return schema.hasEntityWithPluralNamed(term);
    }

    public EntityDefinition getEntityDefinitionWithPluralNamed(final String pluralName){
        return schema.getEntityDefinitionWithPluralNamed(pluralName);
    }

    public EntityDefinition getEntityDefinitionNamed(final String term){
        return schema.getEntityDefinitionNamed(term);
    }


    // Multiple Databases
    public void createInstanceDatabase(String databaseKey) {

        if(databases.containsKey(databaseKey)){
            throw new IllegalStateException("ERM Database Already Exists with name " + databaseKey);
        }

        createInstanceDatabaseIfNotExisting(databaseKey);
    }


    public void deleteInstanceDatabase(String databaseKey) {
        if(databaseKey.equals(DEFAULT_DATABASE_NAME)){
            throw new IllegalStateException("Cannot delete default database");
        }
        databases.remove(databaseKey);
    }

    public void createInstanceDatabaseIfNotExisting(String databaseKey) {
        if(databases.containsKey(databaseKey)){
            return;
        }

        ERInstanceData aDatabase = new ERInstanceData();
        aDatabase.createInstanceCollectionFrom(this.schema);
        databases.put(databaseKey, aDatabase);
    }
}
