# Starfun HubCore

Plugin hub multi-profils pour Folia 1.21.8.

## Prérequis build
- JDK 21 (compilation et API Folia 1.21.8 requièrent Java 21 ; une règle Maven Enforcer bloque les versions <21)
- Maven 3.9+

## Build
```
mvn -DskipTests package
```

Le jar final (ombré) est `target/hubcore-1.21.8.jar`. Le workflow GitHub
Actions `.github/workflows/build.yml` force Java 21 (et exporte JAVA_HOME) pour
éviter l'erreur `invalid target release` et uploade automatiquement l'artifact
`hubcore-jar` pour téléchargement direct depuis l'onglet Actions.

## Sécurité et API
- La clé AES (config `security.encryption.key`) est régénérée automatiquement à
  chaque redémarrage quand `security.encryption.rotate-on-restart=true`. Pour
  une clé fixe (ex. microservice externe), passez la propriété à `false` puis
  définissez une clé Base64 valide.
- Endpoint statut : `api.context-path` (par défaut `/hubcore/status`).
- Endpoint profils : `api.profile-path` (par défaut `/hubcore/profile`) en
  lecture seule, protégé par le token `security.api.token` et chiffrable avec
  la clé AES.
