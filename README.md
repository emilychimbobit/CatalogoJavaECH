# Catálogo de productos en Java 21

Este repositorio contiene el deber del **Día 2** resuelto. Para la práctica del **Día 3**, puedes desplegar el catálogo a Lambda mediante un JAR y conectarlo a API Gateway con o sin el prefijo `/productos`.

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

Compila desde la raíz del proyecto y ejecuta la variante que corresponda a tu deber. Los comandos funcionan en PowerShell, macOS y Linux.

```sh
javac -encoding UTF-8 -d target/classes src/academy/aws/CatalogoHandler.java src/academy/aws/DemoLocal.java
java -cp target/classes academy.aws.DemoLocal 40 con-prefijo
java -cp target/classes academy.aws.DemoLocal 41 sin-prefijo
```

Cada ejecución simula la secuencia completa de llamadas a Lambda con el esquema de rutas elegido y guarda sus datos en `productos.txt`.

| Prueba | Resultado esperado |
|---|---|
| Crear un monitor con POST | `201` y se guarda el producto |
| Repetir la creación | `409` por ID duplicado |
| Actualizar con PUT | `200` con nombre **Monitor HD** y precio **195** |
| Consultar ese ID con GET | `200` con el producto actualizado |
| Listar con GET sin ID | `200` con los productos iniciales y los guardados |
| Actualizar un ID inexistente | `404` |
| Crear con precio negativo | `400` |

Abre `productos.txt` y comprueba que cada ID utilizado aparece una sola vez con los datos actualizados. Para repetir una demo, usa otro ID libre, por ejemplo `42`.

## 3. Rutas del catálogo

Elige el esquema que coincide con tu ejercicio. Las dos opciones de API Gateway usan el mismo controlador Java y el mismo JAR.

| Operación | Con prefijo `/productos` | Sin prefijo `/productos` |
|---|---|---|
| Listar productos | `GET /productos` | `GET /` |
| Consultar un producto | `GET /productos/40` | `GET /?id=40` |
| Crear un producto | `POST /productos` | `POST /` |
| Actualizar un producto guardado en el TXT | `PUT /productos/40` | `PUT /?id=40` |

- **Con prefijo**. Crea en API Gateway las rutas `GET /productos`, `POST /productos`, `GET /productos/{id}` y `PUT /productos/{id}`. Al probar, reemplaza `{id}` por un número. Lambda recibe el ID en `pathParameters.id`.
- **Sin prefijo**. Crea las rutas `GET /`, `POST /` y `PUT /`. Envíale `?id=40` a la URL cuando consultes o actualices un producto específico. Lambda recibe el ID en `queryStringParameters.id`. Usa este mismo formato para las pruebas con **Function URL**.

Conecta todas las rutas de tu variante a la misma Lambda mediante una **HTTP API** con integración **Lambda proxy 2.0**. En Postman, agrega la ruta de prueba después de la URL de invocación de tu etapa.

En POST, envía JSON con `id`, `nombre` y `precio`. En PUT, envía el ID en la URL y un cuerpo como `{"nombre":"Monitor HD","precio":195}`. POST rechaza IDs duplicados, incluidos los de los productos de ejemplo. PUT actualiza los productos creados en el TXT.

La demo local escribe `productos.txt` en la carpeta del proyecto. En Lambda, el archivo se escribe en `/tmp` y cada entorno de ejecución tiene su propia copia. Valida la secuencia de escritura y lectura en local para reproducir los resultados.

## 4. Empaqueta y sube el JAR a Lambda

1. Ejecuta el comando de compilación del paso 2. Luego crea el JAR desde la raíz del proyecto.

   ```sh
   jar --create --file target/catalogo-lambda.jar -C target/classes .
   jar --list --file target/catalogo-lambda.jar
   ```

   En el listado deben aparecer `academy/aws/CatalogoHandler.class` y `academy/aws/CatalogoHandler$Producto.class`. Regenera el JAR cada vez que cambies el código.
2. En la consola de AWS, abre tu función en **Lambda → Código → Cargar desde → Archivo .zip o .jar** y selecciona `target/catalogo-lambda.jar`. También puedes abrir **GitHub → Actions → ejecución del catálogo → Artifacts**, descargar **`catalogo-lambda-jar`**, extraer la descarga y subir el archivo **`catalogo-lambda.jar`** que viene dentro. El artefacto `catalogo-lambda` contiene el ZIP que usa el despliegue automático.
3. Comprueba que el runtime sea **Java 21** y que el controlador sea `academy.aws.CatalogoHandler::handleRequest`.
   Si Lambda muestra `ClassNotFoundException`, compara el nombre de clase del error con el controlador de **Configuración del entorno de ejecución → Editar**. Para este proyecto, `jar --list --file catalogo-lambda.jar` debe mostrar `academy/aws/CatalogoHandler.class` y `academy/aws/CatalogoHandler$Producto.class`. Ejecuta el comando desde la carpeta en la que guardaste el JAR.
4. En **Probar**, ejecuta los eventos de ejemplo disponibles en [`eventos/`](eventos/). Después de integrar las rutas de tu variante en API Gateway, prueba una consulta en Postman. Para comprobar el PUT, primero crea un producto con un ID libre mediante POST.

El flujo de GitHub Actions también se aborda en el [portal del curso](https://aws-developer-academy-liard.vercel.app/).
