<div align="center">

<p align="center">
  <img src="assets/icon.png" alt="EmsiChill icon" width="160">
</p>

# EmsiChill

**Suite modular para servidores Paper**

Autenticación, skins, homes, teletransportes, regiones, tumbas, posturas, resource packs, información de jugadores y herramientas de staff en un solo archivo JAR.

![Java](https://img.shields.io/badge/Java-25-orange?style=flat-square)
![Paper](https://img.shields.io/badge/Paper-26.2-blue?style=flat-square)
![Versión](https://img.shields.io/badge/versión-5.1.5-brightgreen?style=flat-square)
![Licencia](https://img.shields.io/badge/licencia-MIT--0-lightgrey?style=flat-square)

</div>

---

## Qué es

EmsiChill es un plugin multifunción para servidores Paper que quieren una base compacta, configurable y fácil de operar sin instalar diez plugins distintos para tareas comunes. Está pensado para servidores pequeños o medianos donde importan la moderación diaria, la protección contra grief, los datos simples en disco y una experiencia cómoda para jugadores.

No es un reemplazo de redes grandes con bases de datos externas, proxies complejos o paneles web. Su enfoque es otro: un JAR, módulos activables, archivos YAML legibles, comandos claros y mantenimiento desde el juego.

## Alcance

* Autenticación con registro, login, bloqueo de acciones antes de iniciar sesión, sesiones temporales opcionales y protección contra intentos repetidos.
* Skins premium con caché, historial, favoritos, menú de selección, cooldowns y `/skull` para cabezas.
* Homes, TPA, `/back` y RTP con delays configurables, cancelación por movimiento o daño y cooldown para teletransportes aleatorios.
* Regiones de altura completa con propietarios, co-propietarios, miembros, PvP configurable, contenedores públicos opcionales, mejoras de tamaño y compra de cupos.
* Protección de regiones contra construcción ajena, interacciones, entidades, explosiones, pistones, fluidos, fuego y dispensadores.
* Tumbas recuperables con privacidad temporal, expiración configurable y control de muerte por jugador o global.
* Información social: tiempo jugado, ranking, última conexión, posturas `/sit` y `/crawl`, restauración con `/stand` y coordenadas compartibles con `/whereami`.
* Resource packs automáticos al entrar, con soporte para varios paquetes, prompt personalizado, packs opcionales u obligatorios y validación de URL directa más SHA-1.
* Herramientas de staff: staff chat, vanish, staff mode, inspección de inventario y Ender Chest, freeze, mute, warn y registro de sanciones.
* Operación administrativa: reload, status, inspect, backup, migración de datos y avisos de nuevas Releases.

## Contenido

* [Requisitos](#requisitos)
* [Instalación](#instalación)
* [Configuración](#configuración)
* [Actualizaciones](#actualizaciones)
* [Operación segura](#operación-segura)
* [Comandos para jugadores](#comandos-para-jugadores)
* [Comandos de regiones](#comandos-de-regiones)
* [Comandos para administradores y moderadores](#comandos-para-administradores-y-moderadores)
* [Configuración mediante comandos administrativos](#configuración-mediante-comandos-administrativos)
* [Historial de versiones](#historial-de-versiones)
* [Licencia](#licencia)

---

## Requisitos

| Componente | Versión |
| ---------- | ------: |
| Java       |    `25` |
| Paper      |  `26.2` |

> [!IMPORTANT]
> EmsiChill está desarrollado específicamente para Paper. No se garantiza su funcionamiento en Spigot, Bukkit ni otras implementaciones de servidor.

---

## Instalación

1. Descarga la versión más reciente de `EmsiChill` desde **Releases**.
2. Apaga completamente el servidor.
3. Coloca el archivo `.jar` dentro de `plugins/`.
4. Inicia el servidor y espera a que se generen los archivos de configuración.
5. Revisa `plugins/EmsiChill/` antes de abrir el servidor al público.
6. Ejecuta `/emsichill inspect` para detectar problemas obvios de datos o configuración.

> [!TIP]
> Antes de actualizar una instalación existente, crea un respaldo con `/emsichill backup`.

---

## Configuración

La configuración se divide por módulos para que cada archivo tenga un propósito claro.

| Archivo | Controla |
|---|---|
| `config.yml` | Idioma, prefijo, módulos activos, auditoría y actualizaciones. |
| `AuthenticationManager/config.yml` | Registro, login, sesiones, bloqueo de acciones y ubicación de autenticación. |
| `Skin/config.yml` | Timeouts de Mojang, caché, cooldown, favoritos, historial y skins aleatorias. |
| `Teleport/config.yml` | Delays, cancelación por movimiento o daño, TPA, `/back`, homes y RTP. |
| `Home/config.yml` | Límite base de homes y límites por permiso. |
| `Regions/config.yml` | Límite de regiones, distancia mínima, compras, radios de mejora y partículas de visualización. |
| `Graves/config.yml` | Modo de muerte, vida útil de tumbas, privacidad, búsqueda de cofre y expiración. |
| `Staff/config.yml` | Staff chat, vanish, staff mode y tamaño del historial de moderación. |
| `PlayerInfo/config.yml` | Tamaño del ranking de tiempo jugado. |
| `Social/config.yml` | Posturas, `/whereami` y anuncios de sueño. |
| `ResourcePacks/config.yml` | Lista de resource packs enviados al entrar, URL directa, SHA-1, prompt y modo obligatorio u opcional. |
| `messages_es.yml` / `messages_en.yml` | Textos visibles para jugadores y administradores. |

Después de cambiar archivos YAML, usa `/emsichill reload` cuando sea suficiente. Para cambios de entorno, dependencias, permisos del servidor o reemplazo del JAR, reinicia el servidor.

## Actualizaciones

EmsiChill puede comprobar Releases de GitHub y avisar en consola o a administradores con `emsichill.admin.update`. Si la API de GitHub limita o falla, el feed público se usa solo para avisos.

La instalación desde el juego está desactivada por defecto. Para preparar un JAR automáticamente, `updates.install.enabled` debe activarse manualmente y la Release debe venir de la API de GitHub con tamaño y SHA-256 verificados. El archivo se deja en la carpeta oficial de updates de Paper para el siguiente reinicio.

## Operación segura

* Desactiva módulos que no uses desde `config.yml`; los comandos y listeners respetan el estado del módulo.
* `/region delete` exige escribir el nombre de la región y `confirm`.
* Las regiones protegen altura completa y bloquean los vectores típicos de grief, no solo romper y colocar bloques.
* Los teletransportes pueden cancelarse por movimiento o daño, según `Teleport/config.yml`.
* Las tumbas guardan objetos en datos del plugin y se sincronizan al cerrar o recuperar inventarios.
* Las sesiones de autenticación no guardan la IP en texto plano: guardan una firma privada generada con una clave local del servidor. Si la misma cuenta entra desde otra dirección, debe iniciar sesión otra vez.
* Los resource packs están apagados por defecto. Activa `modules.resource-packs` solo después de configurar URLs directas a archivos `.zip` y el SHA-1 exacto de cada archivo.
* Para que Minecraft active los resource packs en cada conexión, EmsiChill debe reenviarlos al entrar. Con el mismo SHA-1, el cliente debería reutilizar su caché en vez de descargar el `.zip` completo.
* `send-only-new-or-changed` evita reenviar packs ya cargados, pero úsalo solo si aceptas que Minecraft podría no reactivarlos en nuevas conexiones.
* Usa `/emsichill status`, `/emsichill inspect` y `/emsichill backup` como rutina antes de tocar configuración sensible.

---

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
| `/emsichill reload` | Recarga las configuraciones del plugin. |
| `/emsichill status` | Muestra el estado de los módulos. |
| `/emsichill inspect` | Busca problemas en datos y configuración. |
| `/emsichill backup` | Crea un respaldo de los datos. |
| `/emsichill migrate` | Guarda y normaliza los datos actuales. |
| `/emsichill help <categoría>` | Muestra ayuda generada por categorías. |

<!-- EMSICHILL_COMMANDS_END -->

---

## Historial de versiones

### Versión actual: `5.1.5`

<details open>
<summary><strong>5.1.5 — TPA interactivo, gateo, cabezas y endurecimiento general</strong></summary>

#### Teletransporte

* Las solicitudes de `/tpa` y `/tpahere` muestran los botones **Aceptar** y **Rechazar** directamente en el chat.
* Los botones ejecutan `/tpaccept` y `/tpdeny`, por lo que conservan las comprobaciones normales de las solicitudes.

#### Posturas

* Se añadió `/crawl` para gatear y desplazarse cerca del suelo.
* `/stand` y la tecla Shift restauran la postura normal.
* Las posturas se limpian al recibir daño, morir, teletransportarse o desconectarse.

#### Cabezas

* Se añadió `/skull <nombre>` para obtener la cabeza de cualquier cuenta premium válida.
* La búsqueda reutiliza el proveedor y la caché de `/skin` sin bloquear el servidor.

#### Seguridad

* `/region delete` exige escribir el nombre de la región y `confirm`.
* Las regiones bloquean explosiones, pistones, fluidos, fuego, dispensadores y cambios de entidades.
* La instalación de updates desde el juego queda desactivada por defecto y requiere metadatos verificados por GitHub.
* Se retiró `/slay` y su permiso para evitar eliminaciones administrativas instantáneas.

#### Módulos

* Los listeners y servicios respetan el estado de cada módulo después de `/emsichill reload`.
* Al desactivar módulos se limpian tareas pendientes, estados temporales y datos en memoria que no deben seguir activos.

#### Resource packs

* Se añadió `ResourcePacks/config.yml` para enviar uno o varios paquetes de recursos cuando un jugador entra al servidor.
* Cada pack puede ser opcional u obligatorio y exige URL directa más SHA-1 válido del `.zip` final.
* `send-only-new-or-changed` permite saltar packs ya cargados por jugador, pero queda apagado por defecto porque Minecraft necesita recibir el pack para activarlo en cada conexión.
* El módulo queda desactivado por defecto para evitar enviar paquetes de ejemplo o enlaces incompletos.

#### Documentación

* El README describe el alcance real del plugin, sus archivos de configuración y las decisiones de seguridad actuales.
* La tabla de comandos se genera desde `plugin.yml` para mantener el README y `/emsichill help` sincronizados.

</details>

<details>
<summary><strong>5.1.4 — Comprobación compatible con hostings compartidos</strong></summary>

#### Actualizaciones

* Cuando un administrador entra al servidor, se repite la comprobación si el resultado almacenado está desactualizado.
* Si la API de GitHub responde con los códigos `403` o `429`, o no se encuentra disponible, EmsiChill consulta el feed público de versiones.
* Después de alcanzar un límite de la API, el plugin espera una hora antes de volver a intentarlo y utiliza el feed durante ese periodo.

#### Instalación

* Se añadió un flujo de instalación desde el juego para preparar el JAR en la carpeta oficial de updates de Paper.
* Desde `5.1.5`, ese flujo queda apagado por defecto y solo acepta metadatos verificados por la API de GitHub.

</details>

<details>
<summary><strong>5.1.3 — Sanciones y notas de actualización</strong></summary>

#### Moderación

* Se añadieron `/mute <jugador> [tiempo]` y `/unmute <jugador>`.
* Los silencios pueden ser permanentes o temporales.
* Se añadieron `/warn <jugador> <motivo>` y `/warnings <jugador>`.

#### Historial

* Las sanciones guardan la fecha, el moderador y el motivo.
* Los registros de sanciones se conservan después de reiniciar el servidor.

#### Actualizaciones

* Los avisos de nuevas versiones incorporan el botón **Ver cambios**.
* Las notas publicadas en GitHub se resumen dentro del juego antes de instalar una actualización.

</details>

<details>
<summary><strong>5.1.2 — Actualización desde el juego</strong></summary>

#### Actualizaciones

* El enlace de una nueva versión puede abrirse directamente desde el chat.
* Los administradores pueden pulsar **Instalar** para preparar el JAR en la carpeta oficial de actualizaciones de Paper.
* La opción **Ignorar** silencia únicamente la versión seleccionada.
* Las versiones posteriores vuelven a mostrarse normalmente.

#### Seguridad

Antes de preparar el archivo JAR, EmsiChill comprueba:

* El nombre del archivo.
* El tamaño.
* La suma de verificación SHA-256.
* La versión.
* La clase principal.

</details>

<details>
<summary><strong>5.1.1 — Congelaciones temporales</strong></summary>

#### Moderación

* `/freeze <jugador> [segundos]` permite definir una duración de entre `1` y `86400` segundos.
* El jugador se libera automáticamente cuando termina el tiempo establecido.

#### Comandos

* Se añadieron sugerencias de duración.
* Se añadieron mensajes más claros para valores incorrectos.

</details>

<details>
<summary><strong>5.1.0 — Herramientas de staff y sistema de actualizaciones</strong></summary>

#### Actualizaciones

* Se añadió `/emsichill update check` para comprobar versiones manualmente.
* Se añadió la comprobación automática de nuevas versiones publicadas en GitHub.

#### Notificaciones

* El servidor muestra un aviso en la consola cuando existe una nueva versión.
* Los administradores con el permiso `emsichill.admin.update` también reciben el aviso.
* El intervalo de comprobación y los destinatarios pueden configurarse desde `config.yml`.

#### Moderación

* Se añadieron `/invsee`, `/enderchestsee` y `/freeze`.

#### Permisos

* Se separaron los permisos necesarios para visualizar y modificar inventarios.

#### Arquitectura

* El sistema de staff se dividió en comandos, eventos, lógica y almacenamiento.

#### Documentación

* Los comandos y permisos comenzaron a generarse automáticamente desde `plugin.yml`.

</details>

---

## Licencia

EmsiChill se distribuye bajo la licencia **MIT No Attribution**, identificada también como **MIT-0**.

Consulta el archivo [`LICENSE`](LICENSE) del proyecto para conocer los términos completos.
