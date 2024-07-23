# WebContify
A headless cms with an ui builder

# Exposed ports
8081

# Open Todos
- [ ] make field configuration updateable
- [ ] change name from field by api to have to be camelCase instead of snake case
- [ ] authentication
- [ ] basic ui
- [ ] permissioning
- [ ] check ui page builder idea
- [ ] split mapping from database and api
- [ ] Handle relations properly in collection item crud ??
- [ ] create swagger ui documentation
- [ ] Recheck code structure
- [ ] check relation handler implementation duplicated code
- [ ] think about making the tests more readable
- [ ] logging
- [ ] Switch FielType Timestamp implementation to offset datetime to handle different timezones in DB
- [ ] Recheck error handling
- [ ] on collection creation check if multiple fields are invalid having proper error response for it
- [ ] think about more granular custom exceptions to do not have the ERROR Code strictly bound to one message
- [ ] handle todos from code
- [ ] computed fields
- [ ] constrains across fields
- [ ] configurable auditing per collection
- [ ] Get jooq implementation properly abstracted, so it does not spread over the whole project
- [ ] Performance testing
- [ ] field migration endpoint changing type of field ??
# Startup
```
docker-compose up -d
./gradlew bootJar
```

```
./gradlew bootTestRun
```
