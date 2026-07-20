<div align="center">

# EmsiChill

**Plugin multifunción para servidores Paper**

Autenticación, skins, homes, teletransportes, regiones, tumbas y herramientas de moderación en un único archivo JAR.

![Java](https://img.shields.io/badge/Java-25-orange?style=flat-square)
![Paper](https://img.shields.io/badge/Paper-26.2-blue?style=flat-square)
![Versión](https://img.shields.io/badge/versión-5.1.5-brightgreen?style=flat-square)
![Licencia](https://img.shields.io/badge/licencia-MIT--0-lightgrey?style=flat-square)

</div>

---

## Características principales

* Sistema de registro e inicio de sesión.
* Gestión de skins premium, favoritas e historial.
* Homes personales y teletransportes entre jugadores.
* Teletransporte aleatorio a ubicaciones seguras.
* Regiones protegidas con miembros, propietarios y mejoras.
* Tumbas recuperables después de morir.
* Consulta del tiempo jugado y de la última conexión.
* Herramientas completas de administración y moderación.
* Sistema integrado de actualizaciones, diagnósticos y respaldos.
* Configuración modular mediante archivos y comandos.

## Contenido

* [Requisitos](#requisitos)
* [Instalación](#instalación)
* [Comandos para jugadores](#comandos-para-jugadores)
* [Comandos de regiones](#comandos-de-regiones)
* [Comandos para administradores y moderadores](#comandos-para-administradores-y-moderadores)
* [Configuración administrativa](#configuración-administrativa)
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

1. Descarga la versión más reciente de `EmsiChill` desde la sección **Releases**.

2. Apaga completamente el servidor.

3. Coloca el archivo `.jar` dentro de la carpeta:

   ```text
   plugins/
   ```

4. Inicia el servidor.

5. Espera a que el plugin genere sus archivos y carpetas de configuración.

6. Configura EmsiChill desde:

   ```text
   plugins/EmsiChill/
   ```

> [!NOTE]
> Después de modificar la configuración, utiliza `/emsichill reload` o reinicia el servidor cuando el cambio lo requiera.

> [!TIP]
> Antes de instalar una nueva versión, es recomendable crear un respaldo con `/emsichill backup`.

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
| `/region delete [nombre]` | Elimina permanentemente una región. |
| `/region help` | Muestra la ayuda de regiones. |

## Comandos para administradores y moderadores

| Comando | Descripción |
|---|---|
| `/invsee <jugador>` | Abre el inventario; modificarlo requiere un permiso adicional. |
| `/enderchestsee <jugador>` | Abre el cofre de Ender; modificarlo requiere un permiso adicional. |
| `/freeze <jugador> [segundos]` | Congela, libera o aplica una congelación con duración definida. |
| `/slay <jugador>` | Elimina inmediatamente a un jugador conectado. |
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
| `/emsichill update changes <versión>` | Muestra dentro del juego un resumen de las notas de la Release. |
| `/emsichill update install <versión>` | Descarga, valida y prepara una Release para el siguiente reinicio. |
| `/emsichill update ignore <versión>` | Oculta los avisos automáticos de una Release concreta. |
| `/emsichill reload` | Recarga las configuraciones del plugin. |
| `/emsichill status` | Muestra el estado de los módulos. |
| `/emsichill doctor` | Busca problemas en datos y configuración. |
| `/emsichill backup` | Crea un respaldo de los datos. |
| `/emsichill migrate` | Guarda y normaliza los datos actuales. |
| `/emsichill help <categoría>` | Muestra ayuda generada por categorías. |

<!-- EMSICHILL_COMMANDS_END -->

---

## Historial de versiones

### Versión actual: `5.1.5`

<details open>
<summary><strong>5.1.5 — TPA interactivo, gateo y cabezas</strong></summary>

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

</details>

<details>
<summary><strong>5.1.4 — Comprobación compatible con hostings compartidos</strong></summary>

#### Actualizaciones

* Cuando un administrador entra al servidor, se repite la comprobación si el resultado almacenado está desactualizado.
* Si la API de GitHub responde con los códigos `403` o `429`, o no se encuentra disponible, EmsiChill consulta el feed público de versiones.
* Después de alcanzar un límite de la API, el plugin espera una hora antes de volver a intentarlo y utiliza el feed durante ese periodo.

#### Instalación

* El método de descarga alternativo mantiene el límite máximo de tamaño.
* El plugin valida el nombre, la versión y la clase principal del archivo JAR.
* La opción `allow-feed-fallback` permite activar o desactivar las instalaciones obtenidas mediante el feed.

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
<summary><strong>5.1.2 — Slay y actualización desde el juego</strong></summary>

#### Moderación

* Se añadió `/slay <jugador>` para eliminar inmediatamente a un jugador conectado.

#### Permisos

* Se añadió el permiso `emsichill.slay`.
* El permiso está disponible para operadores de forma predeterminada.

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
