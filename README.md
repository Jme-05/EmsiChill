<div align="center">

# EmsiChill

**Plugin multifunción para servidores Paper**

Autenticación, skins, homes, teletransportes, regiones, tumbas y herramientas de moderación en un único archivo JAR.

![Java](https://img.shields.io/badge/Java-25-orange?style=flat-square)
![Paper](https://img.shields.io/badge/Paper-26.2-blue?style=flat-square)
![Versión](https://img.shields.io/badge/versión-5.1.4-brightgreen?style=flat-square)
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

### Autenticación

| Comando                                           | Descripción                            |
| ------------------------------------------------- | -------------------------------------- |
| `/register <contraseña> <confirmación>`           | Registra una nueva cuenta              |
| `/login <contraseña>`                             | Inicia sesión en una cuenta registrada |
| `/changepassword <actual> <nueva> <confirmación>` | Cambia la contraseña de la cuenta      |
| `/unregister <contraseña>`                        | Elimina el registro propio             |

### Skins

| Comando                 | Descripción                           |
| ----------------------- | ------------------------------------- |
| `/skin <nombre>`        | Aplica la skin de una cuenta premium  |
| `/skin random`          | Aplica una skin premium aleatoria     |
| `/skin reset`           | Restablece la skin del jugador        |
| `/skin save <nombre>`   | Guarda una skin en favoritos          |
| `/skin unsave <nombre>` | Elimina una skin de favoritos         |
| `/skin favorites`       | Abre el menú de skins favoritas       |
| `/skin history`         | Abre el historial de skins utilizadas |
| `/skin clearhistory`    | Elimina el historial propio de skins  |

### Homes y teletransportes

| Comando              | Descripción                                          |
| -------------------- | ---------------------------------------------------- |
| `/sethome [nombre]`  | Guarda un home en la ubicación actual                |
| `/home [nombre]`     | Se teletransporta inmediatamente a un home           |
| `/delhome <nombre>`  | Elimina un home                                      |
| `/homes`             | Muestra todos los homes propios                      |
| `/tpa <jugador>`     | Solicita teletransportarse hacia otro jugador        |
| `/tpahere <jugador>` | Solicita que otro jugador se teletransporte hacia ti |
| `/tpaccept`          | Acepta una solicitud de teletransporte               |
| `/tpdeny`            | Rechaza una solicitud de teletransporte              |
| `/tpcancel`          | Cancela una solicitud enviada                        |
| `/tptoggle`          | Activa o bloquea las solicitudes de teletransporte   |
| `/back`              | Regresa a la ubicación anterior o a una tumba        |
| `/rtp`               | Busca una ubicación aleatoria segura                 |

### Información y utilidades

| Comando               | Descripción                                        |
| --------------------- | -------------------------------------------------- |
| `/playtime [jugador]` | Consulta el tiempo jugado                          |
| `/playtimetop`        | Muestra la clasificación por tiempo jugado         |
| `/seen [jugador]`     | Consulta la última conexión de un jugador          |
| `/sit`                | Activa o desactiva la postura sentada              |
| `/stand`              | Recupera la postura normal                         |
| `/whereami`           | Comparte la dimensión y las coordenadas en el chat |

### Tumbas

| Comando               | Descripción                       |
| --------------------- | --------------------------------- |
| `/grave list`         | Muestra las tumbas activas        |
| `/grave locate <id>`  | Muestra la ubicación de una tumba |
| `/grave recover <id>` | Recupera una tumba propia         |

---

## Comandos de regiones

### Gestión básica

| Comando                     | Descripción                                       |
| --------------------------- | ------------------------------------------------- |
| `/region claim <nombre>`    | Reclama una región centrada en la posición actual |
| `/region list`              | Muestra las regiones propias y sus coordenadas    |
| `/region info [nombre]`     | Muestra información detallada de una región       |
| `/region teleport <nombre>` | Se teletransporta a una región propia             |
| `/region view [nombre]`     | Muestra temporalmente los límites de una región   |
| `/region delete [nombre]`   | Elimina permanentemente una región                |
| `/region help`              | Muestra la ayuda del sistema de regiones          |

### Compra y ampliación

| Comando                     | Descripción                                    |
| --------------------------- | ---------------------------------------------- |
| `/region build`             | Abre el menú para comprar regiones adicionales |
| `/region upgrade [nombre]`  | Abre el menú de ampliación de una región       |
| `/region settings [nombre]` | Abre la configuración de una región            |

### Miembros y propietarios

| Comando                      | Descripción                                          |
| ---------------------------- | ---------------------------------------------------- |
| `/region add <jugador>`      | Permite que un jugador construya dentro de la región |
| `/region remove <jugador>`   | Elimina a un miembro de la región                    |
| `/region owner <jugador>`    | Añade un propietario secundario                      |
| `/region unowner <jugador>`  | Elimina a un propietario secundario                  |
| `/region transfer <jugador>` | Transfiere la propiedad principal de la región       |

---

## Comandos para administradores y moderadores

### Inspección y control

| Comando                        | Descripción                                                                     |
| ------------------------------ | ------------------------------------------------------------------------------- |
| `/invsee <jugador>`            | Abre el inventario de un jugador; modificarlo requiere un permiso adicional     |
| `/enderchestsee <jugador>`     | Abre el cofre de Ender de un jugador; modificarlo requiere un permiso adicional |
| `/freeze <jugador> [segundos]` | Congela, libera o aplica una congelación temporal                               |
| `/slay <jugador>`              | Elimina inmediatamente a un jugador conectado                                   |
| `/vanish [jugador]`            | Activa o desactiva el modo invisible                                            |
| `/vanishlist`                  | Muestra la lista de jugadores invisibles                                        |
| `/staffmode [jugador]`         | Activa o desactiva las herramientas de moderación                               |

### Sanciones

| Comando                    | Descripción                                                        |
| -------------------------- | ------------------------------------------------------------------ |
| `/mute <jugador> [tiempo]` | Silencia a un jugador permanentemente o durante un tiempo definido |
| `/unmute <jugador>`        | Retira el silencio activo de un jugador                            |
| `/warn <jugador> <motivo>` | Registra una advertencia con fecha, moderador y motivo             |
| `/warnings <jugador>`      | Muestra el historial reciente de sanciones                         |

Ejemplos de duración admitidos por `/mute`:

```text
30s
10m
2h
1d
```

### Comunicación del equipo

| Comando                | Descripción                               |
| ---------------------- | ----------------------------------------- |
| `/staffchat toggle`    | Activa o desactiva el chat administrativo |
| `/staffchat <mensaje>` | Envía un mensaje al equipo administrativo |

### Administración de jugadores

| Comando                                  | Descripción                                                     |
| ---------------------------------------- | --------------------------------------------------------------- |
| `/skin <jugador> <skin>`                 | Cambia la skin de otro jugador                                  |
| `/home <jugador> [home]`                 | Abre y utiliza homes ajenos, incluso de jugadores desconectados |
| `/back <jugador>`                        | Envía a otro jugador a su ubicación anterior                    |
| `/auth unregister <jugador>`             | Elimina administrativamente el registro de una cuenta           |
| `/auth changepassword <jugador> <nueva>` | Cambia administrativamente la contraseña de una cuenta          |
| `/grave admin recover <jugador>`         | Recupera administrativamente una tumba                          |

---

## Configuración administrativa

### Homes, RTP y muertes

| Comando                                       | Descripción                               |
| --------------------------------------------- | ----------------------------------------- |
| `/emsichill homes limit <cantidad>`           | Cambia el límite predeterminado de homes  |
| `/emsichill rtp cooldown <minutos>`           | Cambia el tiempo de espera global del RTP |
| `/deathcontrol default <grave\|keep\|drop>`   | Cambia el modo de muerte predeterminado   |
| `/deathcontrol <jugador> <grave\|keep\|drop>` | Cambia el modo de muerte de un jugador    |

Modos de muerte disponibles:

| Modo    | Comportamiento                         |
| ------- | -------------------------------------- |
| `grave` | Guarda los objetos dentro de una tumba |
| `keep`  | Conserva los objetos después de morir  |
| `drop`  | Deja caer los objetos de manera normal |

### Actualizaciones

| Comando                               | Descripción                                                       |
| ------------------------------------- | ----------------------------------------------------------------- |
| `/emsichill update check`             | Comprueba manualmente si existe una nueva versión                 |
| `/emsichill update changes <versión>` | Muestra dentro del juego un resumen de los cambios                |
| `/emsichill update install <versión>` | Descarga, valida y prepara una versión para el siguiente reinicio |
| `/emsichill update ignore <versión>`  | Oculta los avisos automáticos de una versión concreta             |

### Mantenimiento y diagnóstico

| Comando                       | Descripción                                     |
| ----------------------------- | ----------------------------------------------- |
| `/auth reload`                | Recarga el módulo de autenticación              |
| `/emsichill reload`           | Recarga las configuraciones del plugin          |
| `/emsichill status`           | Muestra el estado de los módulos                |
| `/emsichill doctor`           | Busca problemas en los datos y la configuración |
| `/emsichill backup`           | Crea un respaldo de los datos                   |
| `/emsichill migrate`          | Guarda y normaliza los datos actuales           |
| `/emsichill help <categoría>` | Muestra la ayuda organizada por categorías      |

<!-- EMSICHILL_COMMANDS_END -->

---

## Historial de versiones

### Versión actual: `5.1.4`

<details open>
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
