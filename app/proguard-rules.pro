# Reglas de R8 propias del proyecto. El release minifica (T25).
#
# Hoy no hace falta ninguna: Firebase, Hilt, Room, WorkManager y Compose traen sus propias reglas, y
# la app no usa reflexión sobre sus clases (los documentos de Firestore se leen como mapas, no con
# toObject). Si se suma una librería que sí la usa, o se pasa a leer documentos con clases, va acá.
#
# Para revisar un release minificado: ./gradlew :app:assembleRelease, instalarlo y recorrer la app.
