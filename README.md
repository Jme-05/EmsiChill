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
- **Mantenimiento**: reload, status, inspect, backup, migración y avisos de updates.

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

## Comandos básicos

### Jugadores

| Comando | Uso |
| --- | --- |
| `/register <contraseña> <contraseña>` | Registra una cuenta en servidores offline/cracked. |
| `/login <contraseña>` | Inicia sesión. |
| `/changepassword <actual> <nueva> <nueva>` | Cambia la contraseña. |
| `/skin <nombre>` | Aplica la skin de una cuenta premium. |
| `/skull <nombre>` | Obtiene una cabeza con textura premium. |
| `/sethome [nombre]` | Guarda un home. |
| `/home [nombre]` | Vuelve a un home. |
| `/tpa <jugador>` | Solicita teletransportarte a otro jugador. |
| `/tpaccept` / `/tpdeny` | Acepta o rechaza una solicitud TPA. |
| `/back` | Regresa a tu ubicación anterior o tumba. |
| `/rtp` | Busca un punto aleatorio seguro. |
| `/grave list` | Lista tus tumbas activas. |
| `/grave locate <id>` | Muestra la ubicación de una tumba. |
| `/sit` / `/crawl` / `/stand` | Cambia o restaura posturas. |
| `/whereami` | Comparte dimensión y coordenadas. |

### Regiones

| Comando | Uso |
| --- | --- |
| `/region claim <nombre>` | Reclama una región centrada en tu posición. |
| `/region list` | Lista tus regiones. |
| `/region info [nombre]` | Muestra información de una región. |
| `/region view [nombre]` | Muestra temporalmente los límites. |
| `/region add <jugador>` | Agrega un miembro. |
| `/region remove <jugador>` | Quita un miembro. |
| `/region settings [nombre]` | Abre ajustes de protección. |
| `/region upgrade [nombre]` | Mejora el radio de una región. |
| `/region delete <nombre> confirm` | Elimina una región de forma permanente. |

### Staff y administración

| Comando | Uso |
| --- | --- |
| `/staffchat toggle` | Activa o desactiva el chat de staff. |
| `/vanish [jugador]` | Activa o desactiva vanish. |
| `/staffmode [jugador]` | Activa herramientas de moderación. |
| `/invsee <jugador>` | Abre inventario de un jugador. |
| `/enderchestsee <jugador>` | Abre Ender Chest de un jugador. |
| `/freeze <jugador> [segundos]` | Congela o libera jugadores. |
| `/mute <jugador> [tiempo]` | Silencia temporal o permanentemente. |
| `/warn <jugador> <motivo>` | Registra una advertencia. |
| `/auth unregister <jugador>` | Elimina administrativamente un registro. |
| `/grave admin recover <jugador>` | Recupera una tumba administrativamente. |
| `/emsichill reload` | Recarga configuraciones. |
| `/emsichill status` | Muestra estado de módulos. |
| `/emsichill inspect` | Revisa datos y configuración. |
| `/emsichill backup` | Crea un respaldo. |
| `/emsichill update check` | Busca una nueva Release. |

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
