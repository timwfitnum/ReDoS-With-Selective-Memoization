
# Memoisation Project


## Building project with Maven
```bash
mvn package
```

If everything went according to plan you will see a successful build and 36 existing tests will run and pass.
 
## Running the project
```bash
mvn clean && mvn package

java -jar target/reng-1.0-SNAPSHOT-jar-with-dependencies.jar.
```

### Minor bug fix
If a stackoverflow error occurs try:
```bash
java -jar -Xss10M target/reng-1.0-SNAPSHOT-jar-with-dependencies.jar.
```

Positive and Negative lookaheads have been implemented.

### Bonus Marks:
Backreferences haev also been implemented.
