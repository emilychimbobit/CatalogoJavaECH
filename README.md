# Catálogo de productos en Java 21

Este repositorio contiene el deber del **Día 2** resuelto. Úsalo como punto de partida del **Día 3** para configurar el despliegue continuo a AWS Lambda.

## 1. Obtén tu copia del proyecto

1. En la parte superior de este repositorio en GitHub, selecciona **Fork → Create fork** para crear tu propia copia. Mantén el nombre `CatalogoJavaECH`.
2. En tu copia, selecciona **Code → HTTPS** y copia la URL. En la terminal, usa esa URL en el comando `git clone`.
3. Abre la carpeta en VS Code con **Archivo → Abrir carpeta**. Debes ver `README.md` y `src`.

```sh
git clone https://github.com/TU-USUARIO/CatalogoJavaECH.git
cd CatalogoJavaECH
```

## 2. Compila y prueba en tu computadora

Abre **Terminal → Nueva terminal** en VS Code. Comprueba que `java -version` y `javac -version` muestran 21. Si falta alguno, instala el [JDK 21 de Eclipse Temurin](https://adoptium.net/temurin/releases/?version=21).

Ejecuta estas dos líneas desde la raíz del proyecto. Funcionan en PowerShell, macOS y Linux.

```sh
javac -encoding UTF-8 -d target/classes src/academy/aws/CatalogoHandler.java src/academy/aws/DemoLocal.java
java -cp target/classes academy.aws.DemoLocal 40
```

La demo simula llamadas a la Lambda y usa el mismo `productos.txt` durante toda la secuencia.

| Prueba | Resultado esperado |
|---|---|
| Crear un monitor con POST | `201` y se guarda el producto |
| Repetir la creación | `409` por ID duplicado |
| Actualizar con PUT | `200` con nombre **Monitor HD** y precio **195** |
| Consultar ese ID con GET | `200` con el producto actualizado |
| Listar con GET sin ID | `200` con los productos iniciales y los guardados |
| Actualizar un ID inexistente | `404` |
| Crear con precio negativo | `400` |

Abre `productos.txt` y comprueba que el ID de la prueba aparece una sola vez con los datos actualizados. Para repetirla, usa un ID libre, por ejemplo `41`.

## 3. Rutas del catálogo

| Operación | Function URL | API Gateway |
|---|---|---|
| Listar productos | `GET /` | `GET /productos` |
| Consultar un producto | `GET /?id=40` | `GET /productos/40` |
| Crear un producto | `POST /` | `POST /productos` |
| Actualizar un producto guardado en el TXT | `PUT /?id=40` | `PUT /productos/40` |

En POST, envía JSON con `id`, `nombre` y `precio`. En PUT, envía el ID en la URL y un cuerpo como `{"nombre":"Monitor HD","precio":195}`. POST rechaza IDs duplicados, incluidos los de los productos de ejemplo. PUT actualiza los productos creados en el TXT.

La demo local escribe `productos.txt` en la carpeta del proyecto. En Lambda, el archivo se escribe en `/tmp` y cada entorno de ejecución tiene su propia copia. Valida la secuencia de escritura y lectura en local para reproducir los resultados.

## 4. Continúa con el Día 3

Ten a mano tu repositorio y el nombre y la región de tu Lambda. Sigue el Día 3 del [portal del curso](https://aws-developer-academy-liard.vercel.app/) para configurar GitHub Actions y desplegar desde tu propia copia.

- Runtime de Lambda: **Java 21**.
- Controlador: `academy.aws.CatalogoHandler::handleRequest`.
- Para API Gateway, usa una **HTTP API** con integración Lambda proxy **2.0**. Registra las rutas de la tabla que quieras probar.

Si necesitas comprobar la integración existente desde **Probar** en Lambda, usa los eventos de ejemplo de la carpeta `eventos/`.
