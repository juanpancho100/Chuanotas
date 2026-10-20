# Notas — app Android nativa (HyperOS / Android 16)

App de notas en **Kotlin + Jetpack Compose + Material 3**, pensada para Xiaomi
HyperOS y Android 16. La versión activa es el APK nativo; la PWA anterior queda
solo como prototipo.

## Versión 1.8

Novedades respecto a la 1.7:

- **Asistente de IA con DeepSeek:** nuevo botón con una chispa en el listado,
  junto al micrófono. Es independiente del dictado por voz de siempre.
- **Dictado libre:** hablas una sola vez y la IA convierte lo que dijiste en
  notas con título, cuerpo, categoría y recordatorio. Si en el mismo dictado
  hay varias cosas que hacer, crea varias notas.
- **Vista previa antes de guardar:** las notas que propone la IA se muestran con
  una casilla cada una. Desmarca lo que no quieras y guarda el resto.
- **Preguntas sobre tus notas:** «qué tengo que hacer hoy», «qué tengo que
  hacer mañana», «resumen de la semana». La IA responde solo con lo que tienes
  guardado y cita las notas que usa.
- **Lectura en voz alta:** el botón «Escuchar» lee la respuesta con el motor de
  síntesis del sistema. La respuesta también se puede guardar como nota.
- **API key en Ajustes:** se introduce en *Ajustes → Inteligencia artificial* y
  se guarda cifrada con AES-GCM y una clave del `AndroidKeyStore`. Hay un
  botón para probar la conexión.
- **Privacidad:** solo se envía información cuando usas el asistente, nunca por
  tiempo. La app sigue funcionando igual sin IA y sin conexión.

### Versión 1.7

Novedades respecto a la 1.6:

- **Calendario mensual:** nueva pestaña `MainTab.CALENDAR` con cuadrícula de
  mes, puntos en los días con recordatorios y listado de las notas de cada día.
- **Horarios del día:** nueva pestaña `MainTab.AGENDA` con la agenda diaria y
  navegación por días. Las dos vistas usan `ReminderRepeat.occurrenceOn()`.
- **Repetición por días:** `Note.repeatDays` guarda los días ISO (1 = lunes,
  7 = domingo). Vacío significa una sola vez, que es el valor por defecto.
- **Ventana de calendario, hora y repetición:** `ReminderPickerDialog` sustituye
  a los diálogos sueltos de fecha y hora. Incluye `DatePicker` de Material 3,
  selector de hora y chips para una vez, todos los días, lunes a viernes, fin de
  semana o días personalizados.
- **Reprogramación automática:** `ReminderReceiver` calcula la siguiente
  ocurrencia con `Note.nextReminderAt()` y programa la próxima alarma después de
  mostrar el aviso. `ReminderScheduler.rescheduleAll()` también calcula la
  siguiente ocurrencia tras reiniciar o volver a la app.
- **Compatibilidad:** el JSON antiguo se lee sin `repeatDays` y se interpreta
  como una sola vez. La exportación incluye el campo nuevo.
- **Navegación:** barra inferior con **Notas**, **Calendario** y **Hoy**. El
  editor y Ajustes siguen ocupando la pantalla completa.
- **Pruebas:** se añaden pruebas de cálculo de la próxima repetición y de las
  ocurrencias por día.

### Versión 1.6

Novedades respecto a la 1.5:

- **Pantalla de alarma propia:** `ReminderAlarmActivity` enciende el display,
  aparece sobre el bloqueo y muestra el título, la hora y el texto de la nota.
- **Pantalla completa:** la notificación usa `setFullScreenIntent` cuando el
  sistema lo permite. Se declara `USE_FULL_SCREEN_INTENT`; en Android 14+ el
  usuario puede activarlo desde Ajustes → Recordatorios → **Pantalla completa**.
- **Acciones directas:** la pantalla de alarma ofrece **Abrir nota** y
  **Detener alarma**, y cancela la notificación al cerrar.
- **HyperOS:** nuevo acceso a los permisos especiales de MIUI/HyperOS para
  activar «Mostrar ventanas emergentes» y «Mostrar en pantalla de bloqueo».
- **Prueba completa:** la alarma de prueba en 10 segundos también abre la
  pantalla completa, así que comprueba sonido, pantalla y mensaje.
- **Visibilidad pública:** el canal y la notificación muestran el contenido en la
  pantalla de bloqueo para que el mensaje sea legible.

### Versión 1.5

Novedades respecto a la 1.4:

- **Alarmas de tipo reloj:** `ReminderScheduler` usa `setAlarmClock` cuando hay
  permiso de alarmas exactas. Es el mecanismo más fiable para despertar el
  dispositivo y ejecutar el `BroadcastReceiver` con la app cerrada.
- **Alarmas exactas automáticas en Android 13+:** se declara `USE_EXACT_ALARM`
  además de `SCHEDULE_EXACT_ALARM`. En Android 13+ el sistema concede el permiso
  sin intervención del usuario; en Android 12 se mantiene el acceso manual.
- **Respaldo inexacto:** si no hay permiso exacto o `setAlarmClock` falla, se usa
  `setExactAndAllowWhileIdle` y, como último recurso,
  `setAndAllowWhileIdle`.
- **Diagnóstico de segundo plano:** Ajustes muestra el estado de batería y, en
  HyperOS, un acceso directo al **Inicio automático**.
- **Prueba real de alarma:** botón «Probar alarma en 10 segundos» que programa
  una alarma real mediante `AlarmManager`; sirve para comprobar el funcionamiento
  con la pantalla bloqueada o la app cerrada.
- **Receptores más robustos:** se añaden `USER_UNLOCKED`,
  `QUICKBOOT_POWERON` y el arranque rápido de HTC/Xiaomi a la reprogramación.
  El receptor de alarma comprueba que la nota siga existiendo y tenga
  recordatorio.
- **Permisos opcionales de batería:** se declara
  `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` para poder solicitar la exención desde
  Ajustes. No es obligatorio para usar la app.

### Versión 1.4

- **Nota por voz guiada en 3 pasos:** título, descripción y fecha/hora del
  recordatorio. La captura encadena tres actividades de `RecognizerIntent` y
  conserva el estado entre pasos con `rememberSaveable`.
- **Intérprete de fechas en español** para expresiones como «mañana a las 9»,
  «el viernes a las 18:30», «25 de diciembre a las 10», «en 2 horas» o
  «en 3 días a las 9». Si la fecha no se entiende o ya pasó, se ofrece reintentar
  o guardar la nota sin recordatorio.
- **Recordatorio con tono de alarma:** canal nuevo
  `recordatorios_alarma_v1`, sonido de alarma del sistema con atributos de uso
  de alarma, vibración larga, importancia alta y categoría `CATEGORY_ALARM`.
- **Prueba de sonido** en Ajustes → Recordatorios con el botón
  «Probar sonido de alarma».
- **Pruebas unitarias** del intérprete de fechas en
  `app/src/test/.../SpokenReminderParserTest.kt`.

### Versión 1.3

- **Nota nueva por voz** desde el micrófono del listado, sin pasar por el editor
  vacío.
- **Dictado dentro del editor** para añadir texto al final del cuerpo.
- **Título automático** a partir de la primera línea del dictado cuando la nota
  aún no tiene título.
- El dictado usa `RecognizerIntent` y el servicio de voz del sistema. La app no
  declara ni solicita `RECORD_AUDIO`: el servicio de reconocimiento gestiona el
  micrófono y solo devuelve el texto.

### Versión 1.2

- **Recordatorios opcionales por nota**: elige fecha y hora con los selectores
  del sistema.
- **Notificación a la hora indicada**; al tocarla se abre la nota directamente.
- **Permiso de notificaciones** en Android 13+ (se pide al crear el primer
  recordatorio, no al arrancar).
- **Alarmas exactas opcionales** en Android 12+. Si no se conceden, se usa una
  alarma inexacta y Android puede retrasar el aviso unos minutos.
- **Ajustes → Recordatorios** para revisar y activar notificaciones y alarmas
  exactas.
- **Reprogramación automática** tras reiniciar, actualizar la app o cambiar la
  hora/zona horaria.
- **Indicador de recordatorio** en las tarjetas del listado.
- El campo `reminderAt` se guarda y se incluye en exportar/importar JSON.

### Versión 1.1

- Autoguardado real mientras escribes, además de guardar al salir o al pasar la
  app a segundo plano.
- Deshacer al eliminar desde el aviso inferior.
- Compartir nota con cualquier app del sistema.
- Ajustes: tema del sistema / claro / oscuro, color dinámico de Material You y
  orden del listado.
- Exportar e importar todas las notas en JSON sin permisos de almacenamiento.
- Cuadrícula adaptable para tablets y pantallas grandes.
- APK release firmado, con R8 y reducción de recursos.

## Funciones

- Crear, editar, fijar, buscar y eliminar notas.
- Crear una nota nueva por voz en 3 pasos: título, descripción y recordatorio.
- **Asistente de IA (DeepSeek):** dictado libre que crea las notas y sus
  recordatorios, preguntas sobre tus notas y lectura de la respuesta en voz
  alta. Es opcional y se activa en Ajustes.
- Añadir texto dictado al final del cuerpo desde el editor.
- Pestaña **Calendario** con vista mensual y puntos en los días con recordatorios.
- Pestaña **Hoy** con los horarios del día y navegación por días.
- Recordatorio opcional con notificación de alarma, vibración y apertura directa
  de la nota. Por defecto suena una vez; puede repetirse todos los días, de lunes
  a viernes, los fines de semana o en días personalizados.
- Ventana de calendario, hora y repetición al crear o cambiar una alarma.
- 6 colores de nota adaptados a modo claro/oscuro.
- Persistencia local en `filesDir/notes.json`, con escritura atómica.
- Autoguardado con retardo de 350 ms y volcado inmediato al pasar a segundo plano.
- Copia de seguridad JSON: exportar / importar y fusionar notas.
- Edge-to-edge, icono adaptativo y monocromo, y predictive back para Android 16.
- Sin cuentas y sin permisos de almacenamiento. No se solicita
  permiso de micrófono: el dictado usa el servicio de voz del sistema. Solo se
  solicitan notificaciones y alarmas cuando usas recordatorios. La app no
  usa internet salvo que actives el asistente de IA.

## APK listo para instalar

| Archivo | Uso |
|---|---|
| `dist/Notas-1.8.apk` | Versión **release firmada**. Recomendada para instalar desde cero o actualizar desde la 1.2/1.3/1.4/1.5/1.6/1.7 release. |
| `dist/Notas-1.8-debug.apk` | Versión debug. **Úsala para actualizar la 1.0/1.1/1.2/1.3/1.4/1.5/1.6/1.7 debug sin perder las notas.** |

Detalles:

- Paquete: `com.miking.notas`
- Versión: `1.8` (versionCode 9)
- `minSdk 26` (Android 8.0) · `targetSdk 36` (Android 16)
- Firma release: RSA 2048, APK Signature Scheme v2

### Actualizar sin perder notas

- **1.2/1.3/1.4/1.5/1.6/1.7 release → 1.8 release:** instala `Notas-1.8.apk` encima. La
  clave de firma release es la misma.
- **1.0/1.1/1.2/1.3/1.4/1.5/1.6/1.7 debug → 1.8 debug:** instala
  `Notas-1.8-debug.apk` encima. La clave de debug es la misma.
- **Debug → release o release → debug:** las firmas son distintas. Exporta las
  notas a JSON, desinstala, instala la otra versión e importa el JSON.

### Instalar en HyperOS

1. Copia el APK al teléfono (cable, Drive, Telegram…).
2. Ábrelo desde el explorador de archivos.
3. HyperOS pedirá permitir «instalar apps desconocidas» para esa app; actívalo.
4. Si aparece un aviso de app desconocida, elige **Instalar de todos modos**.

Por ADB:

```bash
adb install -r Notas-1.8-debug.apk   # conserva datos si venías de debug
adb install -r Notas-1.8.apk         # solo si la firma release coincide
```

### Dictado por voz

- El micrófono del listado inicia un flujo guiado de tres capturas:
  1. título;
  2. descripción;
  3. fecha y hora del recordatorio (o «sin recordatorio»).
- El flujo usa `VoiceNoteStep` y `rememberSaveable`, y encadena las actividades
  de `RecognizerIntent` desde `LaunchedEffect`, sin abrir el micrófono desde el
  proceso de Notas.
- `SpokenReminderParser.kt` interpreta fechas y horas en español. Admite «hoy»,
  «mañana», «pasado mañana», días de la semana, fechas como «25/12» o
  «25 de diciembre», cantidades relativas («en 2 horas», «en 3 días») y horas
  como «a las 9», «18:30», «9 y media», «9 de la noche» o «9 pm».
- Si la fecha no se entiende o ya pasó, aparece un diálogo para reintentar el
  tercer paso o guardar la nota sin recordatorio.
- Si se elige un recordatorio, se solicita `POST_NOTIFICATIONS` antes de crear la
  nota. Si se deniega, la nota se guarda igualmente y el editor muestra el aviso
  para activar las notificaciones.
- El micrófono de la barra superior del editor añade el texto al final del cuerpo.
- Si la nota no tiene título, se genera con la primera línea del dictado (máximo
  60 caracteres) para que se pueda identificar en el listado.
- No se solicita `RECORD_AUDIO`. `RecognizerIntent` delega la grabación en la app
  de reconocimiento de voz del dispositivo, que devuelve solo el texto.
- Si no hay ningún servicio de dictado instalado, la app muestra un aviso y sigue
  permitiendo escribir la nota con normalidad.
- La disponibilidad, el idioma y el uso de internet dependen del servicio de voz
  del teléfono. La app no graba ni almacena audio.

### Asistente de IA (DeepSeek)

- Se activa en **Ajustes → Inteligencia artificial**. Es un interruptor, así que
  la app se puede dejar tal cual está, sin IA y sin conexión.
- La **API key** se escribe en el mismo apartado y se guarda cifrada con
  AES-GCM usando una clave del `AndroidKeyStore`. Solo se guarda el texto
  cifrado en las preferencias privadas de la app.
- El botón **«Probar conexión»** manda una petición mínima a DeepSeek para
  comprobar la key y el modelo.
- El **botón con la chispa** del listado abre el asistente. Es independiente
  del micrófono del dictado por voz de siempre: el de la chispa entiende
  frases completas y las convierte en notas.
- El micrófono del asistente usa el mismo servicio de voz del sistema, así que
  **no se pide permiso de micrófono** y no se graba audio.

Cómo funciona el dictado libre:

1. Dices todo lo que tengas que hacer, sin preocuparte del orden: «mañana tengo
   cita con el doctor y luego de eso tengo que ir a comprar pan a las 7 de la
   noche».
2. La IA lo convierte en una nota por cada cosa que mencionaste, con título,
   cuerpo, categoría y recordatorio.
3. Se muestran todas en una vista previa con una casilla cada una. Desmarca
   lo que no quieras.
4. Al guardar se crean las notas y se programan sus alarmas como cualquier
   nota creada a mano.

Preguntas: la pestaña **«Preguntar»** responde con lo que tienes guardado.
«¿Qué tengo que hacer hoy?», «¿Qué tengo que hacer mañana?» o «Resumen de la
semana». El botón **«Escuchar»** lee la respuesta con el motor de síntesis del
sistema, y **«Guardar como nota»** la deja en el listado.

Detalles técnicos:

- La API es la de DeepSeek, compatible con el formato de OpenAI:
  `POST https://api.deepseek.com/chat/completions`. El cliente usa
  `HttpURLConnection` y `org.json`, sin dependencias nuevas.
- El modelo por defecto es `deepseek-flash`. `deepseek-v4-pro` está
  disponible, pero desde septiembre de 2026 sirve la misma familia y cuesta lo
  mismo.
- Las respuestas se piden en modo json (`response_format: json_object`). La
  documentación de DeepSeek avisa de que este modo a veces devuelve el
  contenido vacío, así que el cliente reintenta una vez antes de rendirse.
- Los prompts incluyen siempre la fecha, la hora y la zona horaria del
  dispositivo: sin eso el modelo no puede resolver «mañana» ni «el viernes».
- **El modelo propone y la app decide.** La fecha que devuelve se valida con
  `ReminderRepeat.sanitize()` y se descarta si ya ha pasado, salvo que la nota
  se repita. Solo después se programa la alarma con `ReminderScheduler`.
- Para las preguntas se eligen las notas relevantes de forma local: primero
  las que tienen recordatorio próximo y las que comparten palabras con la
  pregunta, y después las editadas hace más poco. Nunca se envía el
  `notes.json` entero.
- Una llamada cuesta alrededor de $0,0002, así que el uso real sale a céntimos.

### Recordatorios en HyperOS

Para que los avisos lleguen con la app cerrada, enciendan la pantalla y se vean
sobre el bloqueo:

1. En **Ajustes → Recordatorios** de la app, activa notificaciones, **Alarmas
   exactas** y **Pantalla completa** si aparecen como desactivadas.
2. En **Segundo plano**, quita las restricciones de batería.
3. En HyperOS, activa **Inicio automático** y revisa **Permisos de HyperOS**:
   «Mostrar ventanas emergentes» y «Mostrar en pantalla de bloqueo».
4. Android 13 o superior pedirá permiso de notificaciones la primera vez que
   crees un recordatorio.
5. Pulsa **«Probar alarma en 10 segundos»**, bloquea el teléfono y comprueba que
   se enciende la pantalla, suena el tono y aparece el mensaje.

El aviso usa el **tono de alarma del sistema** con `USAGE_ALARM`, no el sonido
habitual de notificación. La notificación tiene importancia alta, categoría de
alarma, vibración larga y visibilidad pública en la pantalla de bloqueo. La
pantalla de alarma se mantiene encendida hasta que pulses **Detener alarma** o
**Abrir nota**. Android reproduce el tono una vez; no es un servicio de alarma
continua. Si el teléfono está en silencio o el canal está bloqueado, el aviso
puede no sonar.

Sin alarma exacta el aviso sigue funcionando, pero Android puede retrasarlo unos
minutos para ahorrar batería.

### Recordatorios repetidos

- `repeatDays` está vacío para una alarma de una sola vez. Los días se guardan
  como valores ISO: 1 = lunes y 7 = domingo.
- `ReminderRepeat.nextOccurrence()` combina la hora original con el siguiente
  día seleccionado. Si la alarma suena con retraso, no repite la ocurrencia
  perdida: salta a la siguiente.
- `ReminderReceiver` publica el aviso y, si la nota se repite, programa la
  siguiente alarma. `ReminderScheduler.rescheduleAll()` hace el mismo cálculo al
  reiniciar, actualizar, desbloquear o cambiar la hora.
- En el calendario, una nota repetida aparece en todos los días seleccionados a
  partir de la fecha inicial. La agenda muestra la hora correspondiente a cada
  día.
- Las notas creadas por voz empiezan como **Una vez**. Después puedes abrir la
  nota y cambiar la repetición.

## Compilar

Requisitos: JDK 17, Android SDK 36 y Build-Tools 36.0.0. El proyecto incluye
Gradle Wrapper 8.13.

```powershell
# Debug
.\gradlew.bat assembleDebug

# Release firmada (necesita keystore.properties)
.\gradlew.bat assembleRelease
```

Salidas:

```
app\build\outputs\apk\debug\app-debug.apk
app\build\outputs\apk\release\app-release.apk
```

`local.properties` lo crea Android Studio automáticamente. Si compilas por consola:

```properties
sdk.dir=C\:\\Users\\tu-usuario\\AppData\\Local\\Android\\Sdk
```

## Pruebas

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
```

Las pruebas cubren el intérprete de fechas habladas y el cálculo de la próxima
ocurrencia de los recordatorios repetidos y del parseo de la respuesta de la IA.
La 1.8 tiene 45 pruebas unitarias.

## Firma release

Este proyecto ya tiene una clave local en:

```
keystore/notas-release.jks
keystore.properties
```

`keystore.properties` contiene las contraseñas y está excluido de Git. **Guarda una
copia de ambos archivos**: si pierdes la clave no podrás actualizar la app instalada
sin desinstalarla antes.

Para usar otra clave, edita `keystore.properties`:

```properties
storeFile=keystore/notas-release.jks
storePassword=TU_CONTRASEÑA
keyAlias=notas
keyPassword=TU_CONTRASEÑA
```

## Estructura

```
app/src/main/java/com/miking/notas/
├── MainActivity.kt              # Entrada, tema dinámico, edge-to-edge y deep link
├── ai/
│   ├── AiAssistant.kt           # Dictado → plan de notas y pregunta → respuesta
│   ├── AiConfig.kt              # Modelos disponibles y nombre legible
│   ├── AiModels.kt              # Plan, nota propuesta, respuesta y parseo del json
│   ├── AiPrompts.kt             # Instrucciones en español y selección de contexto
│   ├── DeepSeekClient.kt        # Cliente HTTP de la API de DeepSeek
│   └── SecretStore.kt           # API key cifrada con AES-GCM en el AndroidKeyStore
├── data/
│   ├── Note.kt                  # Modelo (reminderAt y repeatDays) + JSON
│   ├── ReminderRepeat.kt        # Días ISO, próxima ocurrencia y ocurrencias por día
│   ├── NotesRepository.kt       # Guardado atómico, exportar e importar
│   └── SettingsStore.kt         # Tema, color dinámico, orden y ajustes de IA
├── reminders/
│   ├── ReminderScheduler.kt     # AlarmManager, exactas/inexactas y cancelación
│   ├── ReminderReceiver.kt      # Notifica y reprograma la siguiente repetición
│   ├── ReminderBootReceiver.kt  # Reprograma tras reinicio/actualización/hora
│   ├── ReminderAlarmActivity.kt # Pantalla completa, enciende display y muestra la nota
│   └── ReminderNotifications.kt # Canal, permisos y contenido de la notificación
└── ui/
    ├── NotesApp.kt              # Listado, búsqueda, cuadrícula, FAB de voz y pestañas
    ├── AiAssistantScreen.kt     # Dictado con IA, vista previa y preguntas
    ├── CalendarScreens.kt       # Calendario mensual, agenda diaria y barra inferior
    ├── NoteEditorScreen.kt      # Editor, dictado, colores, recordatorio y compartir
    ├── ReminderPickerDialog.kt  # Calendario, hora y días de repetición
    ├── SettingsScreen.kt        # Ajustes, recordatorios, IA y copia de seguridad
    ├── NotesViewModel.kt        # Estado, autoguardado, dictado y programación de alarmas
    ├── ReminderUi.kt            # Fechas, etiquetas de repetición y accesos a ajustes
    ├── SpeechOutput.kt          # Lectura en voz alta con TextToSpeech
    ├── VoiceNoteUi.kt           # Intent de RecognizerIntent y extracción del texto
    ├── SpokenReminderParser.kt  # Intérprete de fechas/horas habladas en español
    └── theme/Theme.kt           # Material 3, paletas y tipografía

app/src/test/java/com/miking/notas/
├── data/ReminderRepeatTest.kt   # Pruebas de próxima ocurrencia y días repetidos
└── ui/SpokenReminderParserTest.kt # Pruebas unitarias del intérprete de fechas
```

`app/src/main/res/drawable/ic_mic.xml` es el icono vectorial del micrófono; no se
usa `material-icons-extended` para mantener pequeño el APK.

## Detalles técnicos de los recordatorios

- `reminderAt` es un `Long?` con milisegundos epoch; `null` significa sin
  recordatorio. `repeatDays` es un `Set<Int>` con días ISO (1 = lunes, 7 =
  domingo). Vacío significa una sola vez. Las notas antiguas se leen sin el campo
  nuevo y se interpretan como una sola vez.
- `ReminderRepeat.nextOccurrence()` calcula la próxima alarma: para una nota de
  una sola vez devuelve `reminderAt` si es futura; para una repetida combina la
  hora original con el siguiente día seleccionado. `occurrenceOn()` devuelve la
  hora de un día concreto para el calendario y la agenda.
- Se usa `AlarmManager` con `RTC_WAKEUP`. Con permiso de alarmas exactas se usa
  `setAlarmClock` (alarma de tipo reloj, la más fiable en segundo plano y Doze).
  Si falla, se intenta `setExactAndAllowWhileIdle`; sin permiso exacto, se usa
  `setAndAllowWhileIdle`.
- En Android 13+ se declara `USE_EXACT_ALARM`, que el sistema concede
  automáticamente. En Android 12 se mantiene `SCHEDULE_EXACT_ALARM` con acceso
  manual desde Ajustes.
- Cada nota tiene un `PendingIntent` inmutable identificado por su ID; al cambiar
  la hora o los días se reemplaza y al borrar la nota se cancela.
- `ReminderReceiver` comprueba que la nota exista y conserve su recordatorio antes
  de publicar la notificación. Si la nota se repite, calcula la siguiente
  ocurrencia (sin repetir la que acaba de sonar) y programa la próxima alarma.
  También atiende la alarma de prueba.
- `ReminderPickerDialog` reúne el `DatePicker` de Material 3, el selector de hora
  y los chips de repetición. El valor inicial es **Una vez**; las opciones
  rápidas son todos los días, lunes a viernes, fin de semana y personalizado.
- `CalendarScreens.kt` pinta un mes con puntos en los días con recordatorios y
  una agenda diaria. Las dos vistas abren la nota al tocar una ocurrencia.
- El canal de notificación es `recordatorios_alarma_v1` (importancia alta). Se
  crea al arrancar o al publicar el primer aviso. Android no permite cambiar el
  sonido de un canal ya creado, por eso la 1.4 usa un ID nuevo y elimina el canal
  antiguo `recordatorios`.
- El canal usa `RingtoneManager.TYPE_ALARM` (con
  `Settings.System.DEFAULT_ALARM_ALERT_URI` como respaldo),
  `AudioAttributes.USAGE_ALARM`, patrón de vibración largo, luz y visibilidad
  pública en la pantalla de bloqueo.
- La notificación usa `CATEGORY_ALARM`, prioridad máxima, icono monocromo propio
  y abre la nota mediante un extra en `MainActivity`.
- `ReminderNotifications` añade `setFullScreenIntent` cuando
  `NotificationManager.canUseFullScreenIntent()` lo permite. En Android 14+ se
  declara `USE_FULL_SCREEN_INTENT` y Ajustes ofrece el acceso especial para
  activarlo; en versiones anteriores el permiso es automático.
- `ReminderAlarmActivity` es una actividad Compose independiente, con
  `showWhenLocked`, `turnScreenOn` y `FLAG_KEEP_SCREEN_ON`. Se lanza en una tarea
  propia, muestra el título, la hora del aviso, el texto de la nota y, si se
  repite, los días seleccionados. Ofrece **Abrir nota** y **Detener alarma**. Al
  cerrar cancela la notificación.
- `showTest()` publica una notificación de prueba en el mismo canal y también
  abre la pantalla completa de prueba.
- `scheduleTest()` programa una alarma real en 10 segundos con
  `ACTION_TEST_REMINDER`; permite comprobar sonido, pantalla y mensaje con la app
  cerrada y el teléfono bloqueado.
- `BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`, `TIME_SET`, `TIMEZONE_CHANGED`,
  `USER_UNLOCKED`, `QUICKBOOT_POWERON` y la concesión del permiso de alarmas
  exactas reprograman la próxima ocurrencia de cada recordatorio.
- Ajustes comprueba `PowerManager.isIgnoringBatteryOptimizations` y ofrece abrir
  la exención de batería. En HyperOS añade accesos directos al **Inicio
  automático** y a los **permisos especiales** (`com.miui.securitycenter`), con
  respaldo a los detalles de la app.

## Detalles técnicos del dictado

- Se usa `ActivityResultContracts.StartActivityForResult` con
  `RecognizerIntent.ACTION_RECOGNIZE_SPEECH`; no se inicializa `SpeechRecognizer`
  ni se abre el micrófono desde el proceso de Notas.
- `EXTRA_LANGUAGE` toma el idioma actual del teléfono y `EXTRA_MAX_RESULTS` se
  limita a uno. El primer resultado de `EXTRA_RESULTS` es el texto que se usa.
- Si no hay actividad de reconocimiento, `launch()` lanza
  `ActivityNotFoundException`; se captura y se muestra un aviso.
- `NotesViewModel.createFromVoice(title, body, reminderAt)` crea la nota, programa
  el recordatorio si existe, la guarda y la abre. `appendDictation` añade el texto
  al cuerpo y completa el título solo si estaba vacío.
- No hay audio en `notes.json`, en la exportación ni en los permisos del
  manifiesto; solo se persiste el texto.

## Datos y privacidad

- Todo se guarda en el almacenamiento privado de la app. No se piden permisos de
  almacenamiento.
- La app declara el permiso `INTERNET`, pero **solo lo usa el asistente de IA**
  y únicamente cuando tú lo activas. Sin IA activada no sale ningún dato.
- Al usar el asistente, el texto que dictas y las notas seleccionadas como
  contexto se envían a la API de DeepSeek. Lee su política de privacidad antes
  de usarlo con contenido sensible.
- La API key se cifra con AES-GCM y una clave guardada en el `AndroidKeyStore`,
  así que no sale del dispositivo en texto plano.
- El JSON exportado contiene tus notas, recordatorios y días de repetición en
  texto plano; guárdalo en un lugar seguro.
- Al importar, se conservan las notas actuales. Solo se añaden notas nuevas o con
  `updatedAt` más reciente.
- Los permisos `POST_NOTIFICATIONS`, `VIBRATE`, `SCHEDULE_EXACT_ALARM`,
  `USE_EXACT_ALARM`, `USE_FULL_SCREEN_INTENT`, `RECEIVE_BOOT_COMPLETED` y
  `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` solo se usan para los recordatorios. La
  exención de batería es opcional y se solicita desde Ajustes. No se declara
  `RECORD_AUDIO`: el dictado lo gestiona el servicio de voz del sistema y la app
  solo recibe el texto reconocido.
- La pantalla de alarma muestra el título y el texto de la nota sobre el bloqueo.
  Si tienes notas sensibles, ten en cuenta que ese contenido puede verse sin
  desbloquear el teléfono mientras suena el recordatorio.
- El texto dictado se envía al servicio de reconocimiento del dispositivo. Según
  el fabricante puede usar internet o procesarse localmente; consulta la política
  de privacidad de ese servicio si te preocupa el audio.

## Próximas ideas

- Posponer o marcar como hecha desde la notificación.
- Varias alarmas por nota y horarios distintos dentro del mismo día.
- Etiquetas/carpetas y papelera.
- Listas de verificación.
- Bloqueo con huella o rostro.
- Widget de HyperOS con Glance.
- Sincronización opcional con la nube.
