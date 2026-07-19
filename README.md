# EmsiChill

Plugin multifunción para servidores Paper. Reúne autenticación, skins, homes, teletransportes,
regiones, tumbas y herramientas de moderación en un solo JAR.

## Requisitos

- Java 25
- Paper 26.2

## Instalación

1. Descarga `EmsiChill` desde Releases.
2. Apaga el servidor.
3. Coloca el JAR en `plugins/`.
4. Inicia el servidor.
5. Configura el plugin desde `plugins/EmsiChill/`.

<!-- EMSICHILL_COMMANDS_START -->

### 5.0.0

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
| `/sethome [nombre]` | Guarda un home. |
| `/home [nombre]` | Se teletransporta inmediatamente a un home. |
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
| `/region delete [nombre]` | Elimina permanentemente una región. |
| `/region help` | Muestra la ayuda de regiones. |

## Comandos para administradores y moderadores

| Comando | Descripción |
|---|---|
| `/invsee <jugador>` | Abre el inventario; modificarlo requiere un permiso adicional. |
| `/enderchestsee <jugador>` | Abre el cofre de Ender; modificarlo requiere un permiso adicional. |
| `/freeze <jugador>` | Congela o libera temporalmente a un jugador conectado. |
| `/staffchat toggle` | Activa o desactiva el chat administrativo. |
| `/staffchat <mensaje>` | Envía un mensaje al equipo. |
| `/vanish [jugador]` | Activa o desactiva el modo invisible. |
| `/vanishlist` | Lista los jugadores invisibles. |
| `/staffmode [jugador]` | Activa las herramientas de moderación. |
| `/skin <jugador> <skin>` | Cambia la skin de otro jugador. |
| `/home <jugador> [home]` | Abre y utiliza homes ajenos, incluso desconectados. |
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
| `/emsichill reload` | Recarga las configuraciones del plugin. |
| `/emsichill status` | Muestra el estado de los módulos. |
| `/emsichill doctor` | Busca problemas en datos y configuración. |
| `/emsichill backup` | Crea un respaldo de los datos. |
| `/emsichill migrate` | Guarda y normaliza los datos actuales. |
| `/emsichill help <categoría>` | Muestra ayuda generada por categorías. |

<!-- EMSICHILL_COMMANDS_END -->

## Historial de versiones

### 5.1.0

## Comandos para administradores y moderadores

| Área | Cambio |
|---|---|
| Actualizaciones | Se añadió `/emsichill update check` para comprobar Releases manualmente. |
| Actualizaciones | Se añadió la comprobación automática de nuevas Releases de GitHub. |
| Notificaciones | El servidor avisa en consola y a los administradores con `emsichill.admin.update` cuando existe una versión nueva. |
| Configuración | El intervalo y los destinatarios del aviso pueden configurarse en `config.yml`. |
| Moderación | Se añadieron `/invsee`, `/enderchestsee` y `/freeze`. |
| Permisos | Se separaron los permisos para mirar y modificar inventarios. |
| Arquitectura | Se dividió el sistema de staff en comandos, eventos, lógica y almacenamiento. |
| Documentación | Los comandos y permisos pasaron a generarse desde `plugin.yml`. |

## Licencia

EmsiChill utiliza la licencia MIT No Attribution.
