# WebContify
A headless cms with an ui builder

# Exposed ports
8081

# Open Todos
- [ ] Finish Relation implementation and create tests
- field type configuration validation tests
- relation mirror field checking how it behaves in tests
- [ ] split mapping from database and api
- [ ] handle update and delete properly in database too
- [ ] Recheck collection field updates which are referenced in relations
- [ ] Check if collection field name update works with foreign keys
- [ ] Handle relations properly in collection item crud
- [ ] check transaction boundaries in collection creation endpoint
- [ ] create swagger ui documentation
- [ ] moving postman collections and environments into repository
- [ ] Recheck code structure
- [ ] logging
- [ ] Switch FielType Timestamp implementation to offset datetime to handle different timezones in DB
- [ ] make format configurable
- [ ] Recheck error handling
- [ ] on collection creation check if multiple fields are invalid having proper error response for it
- [ ] think about more granular custom exceptions to do not have the ERROR Code strictly bound to one message
- [ ] Make field configuration fields update able
- [ ] handle todos from code
- [ ] computed fields
- [ ] constrains across fields
- [ ] configurable auditing per collection
- [ ] Get jooq implementation properly abstracted, so it does not spread over the whole project
- [ ] Performance testing
- [ ] Spring Security integration
- [ ] UI Implementation
- [ ] TBD has to be merged with other project which has to be migrated too kotlin first
- [ ] field migration endpoint changing type of field
# Startup
```
docker-compose up -d
./gradlew bootJar
```

```
./gradlew bootTestRun
```
