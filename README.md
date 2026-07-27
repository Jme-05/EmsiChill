<div align="center">

<p>
  <img src="assets/icon.png" alt="EmsiChill icon" width="150">
</p>

# EmsiChill

**Suite modular para servidores Paper**

Autenticación, skins, homes, teletransportes, regiones, tumbas, posturas, resource packs, información de jugadores y herramientas de staff en un solo plugin.

![Java](https://img.shields.io/badge/Java-25-orange?style=flat-square)
![Paper](https://img.shields.io/badge/Paper_API-26.2-blue?style=flat-square)
![Version](https://img.shields.io/badge/version-5.1.5-brightgreen?style=flat-square)
![License](https://img.shields.io/badge/license-MIT--0-lightgrey?style=flat-square)

</div>

---

## Qué es EmsiChill

EmsiChill es un plugin para servidores **Paper** que junta varias funciones comunes de un servidor survival o semi-survival en un solo JAR. Está pensado para servidores pequeños o medianos que quieren una base ordenada sin depender de muchos plugins separados para autenticación, teleports, regiones, tumbas, staff y datos simples.

El plugin trabaja por módulos. Puedes activar o desactivar funciones desde `plugins/EmsiChill/config.yml` sin borrar los datos guardados de cada módulo.

> La documentación completa y explicada con más detalle estará en la página del portafolio. Este README solo resume lo importante para GitHub.

## Requisitos

| Requisito | Versión |
| --- | --- |
| Java | `25` |
| Servidor | `Paper` |
| Paper API | `26.2` |

No se garantiza soporte para Spigot, Bukkit u otros forks que no sigan el comportamiento de Paper.

## Funciones principales

- **Auth**: registro, login, cambio de contraseña y sesiones temporales opcionales.
- **Skins**: skins premium, favoritas, historial, skins aleatorias y cabezas con `/skull`.
- **Homes y teleport**: `/home`, `/sethome`, TPA, `/back` y RTP seguro.
- **Regiones**: claims con miembros, co-owners, upgrades, settings y protecciones contra grief.
- **Tumbas**: recuperación de items al morir, privacidad temporal y expiración configurable.
- **Posturas y social**: `/sit`, `/crawl`, `/stand`, `/whereami`, `/seen` y tiempo jugado.
- **Resource packs**: envío automático de packs con URL directa y SHA-1.
- **Staff**: vanish, staffmode, staffchat, invsee, enderchestsee, freeze, mute y warn.
- **Mantenimiento**: reload, status, inspect, backup, migración, updates de EmsiChill y avisos de builds nuevas de Paper.

## Instalación rápida

1. Descarga el `.jar` desde la sección de Releases.
2. Apaga el servidor.
3. Coloca el `.jar` en la carpeta `plugins/`.
4. Inicia el servidor para generar `plugins/EmsiChill/`.
5. Revisa la configuración antes de abrir el servidor al público.
6. Usa `/emsichill inspect` para detectar problemas básicos.

Para actualizar una instalación existente, crea un respaldo antes:

```mcfunction
/emsichill backup
```

## Configuración

La configuración se guarda dentro de `plugins/EmsiChill/`.

Archivos importantes:

- `config.yml`: idioma, prefijo, módulos activos, auditoría y updates.
- `messages_es.yml` / `messages_en.yml`: mensajes visibles del plugin.
- `AuthenticationManager/config.yml`: registro, login, sesiones y bloqueos antes de iniciar sesión.
- `Skin/config.yml`: caché, cooldowns, favoritas, historial y skins aleatorias.
- `Teleport/config.yml`: TPA, `/back`, RTP, delays y cancelaciones.
- `Home/config.yml`: límite base de homes y límites por permiso.
- `Regions/config.yml`: claims, radios, upgrades, settings y límites.
- `Graves/config.yml`: modo de muerte, tumbas, privacidad y expiración.
- `Staff/config.yml`: staffchat, vanish, staffmode y moderación.
- `ResourcePacks/config.yml`: packs enviados al entrar, URL, SHA-1 y modo obligatorio/opcional.

Después de cambiar YAML puedes probar:

```mcfunction
/emsichill reload
```

Para cambiar el JAR, actualizar dependencias o tocar configuraciones críticas, reinicia el servidor completo.

<!-- EMSICHILL_COMMANDS_START -->

## Comandos para jugadores

| Comando | Descripción |
|---|---|
| `/register <contraseña> <contraseña>` | Registra una cuenta. |
| `/login <contraseña>` | Inicia sesión. |
| `/changepassword <actual> <nueva> <nueva>` | Cambia la contraseña. |
| `/unregister <contraseña>` | Elimina el registro propio. |
| `/skin <nombre>` | Aplica la skin de una cuenta premium. |
| `/skin random` | Aplica una skin premium aleatoria. |
| `/skin reset` | Restablece la skin. |
| `/skin save <nombre>` | Guarda una skin como favorita. |
| `/skin unsave <nombre>` | Elimina una skin de favoritos. |
| `/skin favorites` | Abre el menú de skins favoritas. |
| `/skin history` | Abre el historial de skins. |
| `/skin clearhistory` | Elimina el historial propio. |
| `/skull <nombre>` | Obtiene la cabeza de una cuenta premium. |
| `/sethome [nombre]` | Guarda un home. |
| `/home [nombre]` | Se teletransporta a un home respetando el delay configurado. |
| `/delhome <nombre>` | Elimina un home. |
| `/homes` | Muestra todos los homes propios. |
| `/tpa <jugador>` | Solicita teletransportarse a otro jugador. |
| `/tpahere <jugador>` | Solicita que otro jugador vaya hacia ti. |
| `/tpaccept` | Acepta una solicitud de teletransporte. |
| `/tpdeny` | Rechaza una solicitud de teletransporte. |
| `/tpcancel` | Cancela una solicitud enviada. |
| `/tptoggle` | Activa o bloquea las solicitudes. |
| `/back` | Regresa a la ubicación anterior o a la tumba. |
| `/rtp` | Busca un lugar aleatorio seguro. |
| `/playtime [jugador]` | Consulta el tiempo jugado. |
| `/playtimetop` | Muestra la clasificación de tiempo jugado. |
| `/seen [jugador]` | Consulta la última conexión. |
| `/sit` | Activa o desactiva la postura sentada. |
| `/crawl` | Activa o desactiva la postura de gateo. |
| `/stand` | Recupera la postura normal. |
| `/whereami` | Comparte dimensión y coordenadas en el chat. |
| `/grave list` | Muestra las tumbas activas. |
| `/grave locate <id>` | Muestra la ubicación de una tumba. |
| `/grave recover <id>` | Recupera una tumba propia. |

## Comandos de regiones

| Comando | Descripción |
|---|---|
| `/region claim <nombre>` | Reclama una región centrada en tu posición. |
| `/region list` | Lista tus regiones y coordenadas. |
| `/region info [nombre]` | Muestra información de una región. |
| `/region teleport <nombre>` | Se teletransporta a una región propia. |
| `/region view [nombre]` | Muestra temporalmente sus límites. |
| `/region build` | Abre el menú para comprar más regiones. |
| `/region upgrade [nombre]` | Abre el menú de ampliación. |
| `/region settings [nombre]` | Abre la configuración de la región. |
| `/region add <jugador>` | Permite construir a un miembro. |
| `/region remove <jugador>` | Elimina a un miembro. |
| `/region owner <jugador>` | Añade un propietario secundario. |
| `/region unowner <jugador>` | Elimina un propietario secundario. |
| `/region transfer <jugador>` | Transfiere el propietario principal. |
| `/region delete <nombre> confirm` | Elimina permanentemente una región. |
| `/region help` | Muestra la ayuda de regiones. |

## Comandos para administradores y moderadores

| Comando | Descripción |
|---|---|
| `/invsee <jugador>` | Abre inventario, armadura y mano secundaria; modificarlo requiere un permiso adicional. |
| `/enderchestsee <jugador>` | Abre el cofre de Ender; modificarlo requiere un permiso adicional. |
| `/freeze <jugador> [segundos]` | Congela, libera o aplica una congelación con duración definida. |
| `/mute <jugador> [tiempo]` | Silencia permanentemente o durante 30s, 10m, 2h o 1d. |
| `/unmute <jugador>` | Retira el silencio activo de un jugador. |
| `/warn <jugador> <motivo>` | Registra una advertencia con fecha, moderador y motivo. |
| `/warnings <jugador>` | Muestra el historial reciente de sanciones. |
| `/staffchat toggle` | Activa o desactiva el chat administrativo. |
| `/staffchat <mensaje>` | Envía un mensaje al equipo. |
| `/vanish [jugador]` | Activa o desactiva el modo invisible. |
| `/vanishlist` | Lista los jugadores invisibles. |
| `/staffmode [jugador]` | Activa las herramientas de moderación. |
| `/skin <jugador> <skin>` | Cambia la skin de otro jugador. |
| `/home <jugador> [home]` | Lista o utiliza homes ajenos, incluso de jugadores desconectados. |
| `/back <jugador>` | Envía a otro jugador a su ubicación anterior. |
| `/auth unregister <jugador>` | Elimina administrativamente el registro de una cuenta. |
| `/auth changepassword <jugador> <nueva>` | Cambia administrativamente una contraseña. |
| `/grave admin recover <jugador>` | Recupera administrativamente una tumba. |

## Configuración mediante comandos administrativos

| Comando | Descripción |
|---|---|
| `/emsichill homes limit <cantidad>` | Cambia el límite predeterminado de homes. |
| `/emsichill rtp cooldown <minutos>` | Cambia el cooldown global de RTP. |
| `/deathcontrol default <grave\|keep\|drop>` | Cambia el modo de muerte predeterminado. |
| `/deathcontrol <jugador> <grave\|keep\|drop>` | Cambia el modo de muerte de un jugador. |
| `/auth reload` | Recarga el módulo de autenticación. |
| `/emsichill update check` | Comprueba si existe una Release nueva sin instalarla. |
| `/emsichill update changes <versión>` | Muestra dentro del juego un resumen de las notas de la Release. |
| `/emsichill update install <versión>` | Si la instalación está habilitada, descarga, valida y prepara una Release. |
| `/emsichill update ignore <versión>` | Oculta los avisos automáticos de una Release concreta. |
| `/emsichill update paper check` | Comprueba si PaperMC publico una build nueva para Paper/Minecraft. |
| `/emsichill update paper download <version> <build>` | Descarga y verifica una build nueva de Paper para aplicarla en el siguiente reinicio. |
| `/emsichill update paper ignore <version> <build>` | Oculta los avisos automaticos de una build concreta de Paper. |
| `/emsichill reload` | Recarga las configuraciones del plugin. |
| `/emsichill status` | Muestra el estado de los módulos. |
| `/emsichill inspect` | Busca problemas en datos y configuración. |
| `/emsichill backup` | Crea un respaldo de los datos. |
| `/emsichill migrate` | Guarda y normaliza los datos actuales. |
| `/emsichill help <categoría>` | Muestra ayuda generada por categorías. |

<!-- EMSICHILL_COMMANDS_END -->

## Actualizaciones de Paper/Minecraft

EmsiChill puede revisar automáticamente si PaperMC publicó una build nueva de Paper para Minecraft. Si encuentra una build más reciente, avisa en consola y a los administradores con permiso `emsichill.admin.update`.

Por seguridad, el plugin **no reemplaza el JAR del servidor mientras está corriendo**. Solo descarga el nuevo `paper-*.jar`, comprueba su tamaño y SHA-256, y lo deja preparado en una carpeta para que el administrador lo aplique en el siguiente reinicio.

Flujo recomendado:

```mcfunction
/emsichill update paper check
/emsichill update paper download <version> <build>
```

Después de descargarlo:

1. Apaga el servidor.
2. Ve a la carpeta `server-updates/`.
3. Reemplaza el JAR actual del servidor por el `paper-*.jar` descargado.
4. Inicia el servidor otra vez.
5. Revisa la consola y usa `/emsichill inspect`.

Configuración principal en `plugins/EmsiChill/config.yml`:

```yaml
updates:
  paper:
    enabled: true
    project: paper
    include-experimental-builds: false
    automatic:
      enabled: true
      interval-minutes: 30
      notify-console: true
      notify-admins: true
    download:
      enabled: true
      directory: server-updates
      max-download-megabytes: 120
```

`include-experimental-builds: false` usa solo builds estables. Si lo cambias a `true`, EmsiChill también podrá detectar builds beta o experimentales, pero no es lo ideal para un servidor público.

## Datos guardados

EmsiChill usa archivos YAML locales dentro de `plugins/EmsiChill/`.

Ejemplos de datos guardados:

- usuarios registrados y sesiones de auth;
- skins elegidas, favoritas e historial;
- homes, cooldowns y preferencias de teleport;
- regiones, miembros, owners y settings;
- tumbas activas;
- sanciones, mutes, warnings y staffmode;
- playtime, first seen y last seen.

Las contraseñas no se guardan en texto plano. El módulo de auth usa hash con sal para guardar credenciales de forma más segura.

## Notas importantes

- Los módulos desactivados desde `config.yml` no deberían seguir ejecutando comandos ni listeners activos.
- Los resource packs están pensados para URL directa a `.zip` y SHA-1 real del archivo.
- `/region delete` requiere `confirm` para evitar borrados accidentales.
- `/emsichill reload` no reemplaza un reinicio cuando cambias el JAR o actualizas Paper.
- Para servidores públicos, revisa permisos antes de abrir el servidor.

## Licencia

EmsiChill usa licencia **MIT No Attribution (MIT-0)**.

Consulta [`LICENSE`](LICENSE) para ver los términos completos.
