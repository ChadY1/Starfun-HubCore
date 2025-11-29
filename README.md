# Starfun HubCore

Plugin hub multi-profils pour Spigot/Paper 1.9.4 avec messagerie Bungeecord.

## Prérequis build
- JDK 8 (compatibilité ciblée Paper/Spigot 1.9.4)
- Maven 3.9+

## Build
```
mvn -DskipTests package
```

Le jar final (ombré) est `target/hubcore-1.9.4.jar`. Le workflow GitHub Actions
`.github/workflows/build.yml` force Java 8 pour éviter l'erreur `invalid target
release` et uploade automatiquement l'artifact `hubcore-jar` pour
téléchargement direct depuis l'onglet Actions.

## Sécurité et API
- La clé AES (config `security.encryption.key`) est régénérée automatiquement à
  chaque redémarrage quand `security.encryption.rotate-on-restart=true`. L'ancienne
  clé est déplacée dans `security.encryption.previous-key` pour permettre de
  relire les données chiffrées avant rotation. Pour une clé fixe (ex. microservice
  externe), passez la propriété à `false` puis définissez une clé Base64 valide
  (vous pouvez aussi renseigner manuellement `previous-key` si vous migrez depuis
  une clé plus ancienne).
- Les mots de passe joueurs sont hachés via PBKDF2 + salt par compte, avec
  un **pepper configurable** et un nombre d'itérations ajustable via
  `security.password.pepper` et `security.password.iterations`.
- Endpoint statut : `api.context-path` (par défaut `/hubcore/status`).
- Endpoint profils : `api.profile-path` (par défaut `/hubcore/profile`) en
  lecture seule, protégé par le token `security.api.token` et chiffrable avec
  la clé AES.
- Section `bungeecord` : active/désactive le channel PluginMessaging (par
  défaut `BungeeCord`) et expose des URLs réseau (`network-api-base`,
  `games-status-endpoint`) pour raccorder les menus et microservices aux APIs
  externes.
