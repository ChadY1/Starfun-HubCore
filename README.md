# Starfun HubCore

Plugin hub multi-profils pour Folia 1.21.8.

## Prérequis build
- JDK 21 (compilation et API Folia 1.21.8 requièrent Java 21)
- Maven 3.9+

## Build
```
mvn -DskipTests package
```
Le workflow GitHub Actions `.github/workflows/build.yml` utilise également Java 21 pour éviter l'erreur `invalid target release`.
