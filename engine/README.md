# Simulation (Maven)

This module is a small Java simulator that talks to the Fast Pin Pon API.

## Build

```sh
cd simulation
chmod +x mvnw
./mvnw -q package
```

## Run

```sh
cd simulation
./mvnw -q exec:java
```

Or run the shaded jar:

```sh
cd simulation
java -jar target/simulation-0.1.0-SNAPSHOT-shaded.jar
```
