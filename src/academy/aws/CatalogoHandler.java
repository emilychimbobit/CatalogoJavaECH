package academy.aws;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CatalogoHandler {
    private static final String TEXTO_JSON = "\"(?:\\\\(?:[\"\\\\/bfnrt]|u[0-9a-fA-F]{4})|[^\"\\\\\\x00-\\x1f])*\"";
    private static final String NUMERO_JSON = "-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?";
    private static final Pattern CAMPO_JSON = Pattern.compile(
        "\\s*(?<clave>" + TEXTO_JSON + ")\\s*:\\s*"
            + "(?<valor>" + TEXTO_JSON + "|" + NUMERO_JSON + "|true|false|null)\\s*"
    );
    private static final Map<String, String> PRODUCTOS = Map.of(
        "1", """
            {"id":1,"nombre":"Teclado","precio":25}
            """.strip(),
        "2", """
            {"id":2,"nombre":"Mouse","precio":15}
            """.strip(),
        "3", """
            {"id":3,"nombre":"Audífonos","precio":35}
            """.strip()
    );

    public Map<String, Object> handleRequest(Map<String, Object> evento) {
        return procesarEvento(evento);
    }

    // Las operaciones sobre el TXT comparten un bloqueo dentro de esta JVM.
    private static synchronized Map<String, Object> procesarEvento(Map<String, Object> evento) {
        try {
            return switch (obtenerMetodo(evento)) {
                case "GET" -> consultar(evento);
                case "POST" -> crear(evento);
                case "PUT" -> actualizar(evento);
                default -> respuesta(405, errorJson("Método no permitido"));
            };
        } catch (IllegalArgumentException excepcion) {
            return respuesta(400, errorJson(excepcion.getMessage()));
        } catch (IOException excepcion) {
            return respuesta(500, errorJson("No se pudo leer o guardar el archivo de productos"));
        }
    }

    private static Map<String, Object> consultar(Map<String, Object> evento) throws IOException {
        String id = obtenerId(evento);
        if (id == null && !esRutaDeProducto(evento)) {
            var catalogo = new LinkedHashMap<String, String>();
            PRODUCTOS.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(producto -> catalogo.put(producto.getKey(), producto.getValue()));
            leerProductos().forEach(catalogo::putIfAbsent);
            return respuesta(200, "[" + String.join(",", catalogo.values()) + "]");
        }

        id = validarId(id);
        String producto = PRODUCTOS.get(id);
        if (producto == null) {
            producto = leerProductos().get(id);
        }
        return producto == null
            ? respuesta(404, errorJson("Producto no encontrado"))
            : respuesta(200, producto);
    }

    private static Map<String, Object> crear(Map<String, Object> evento) throws IOException {
        var campos = leerCuerpo(evento);
        String id = validarId(valorId(campos));
        Producto nuevo = construirProducto(id, campos);
        var guardados = leerProductos();
        if (PRODUCTOS.containsKey(id) || guardados.containsKey(id)) {
            return respuesta(409, errorJson("Ya existe un producto con ese ID"));
        }

        guardados.put(id, nuevo.aJson());
        guardarProductos(guardados);
        return respuesta(201, nuevo.aJson());
    }

    private static Map<String, Object> actualizar(Map<String, Object> evento) throws IOException {
        String id = validarId(obtenerId(evento));
        var campos = leerCuerpo(evento);
        if (campos.containsKey("id")) {
            throw new IllegalArgumentException("Envía el ID en la URL y solo nombre y precio en el cuerpo");
        }
        Producto actualizado = construirProducto(id, campos);
        var guardados = leerProductos();
        if (PRODUCTOS.containsKey(id) || !guardados.containsKey(id)) {
            return respuesta(404, errorJson("Producto no encontrado en el TXT"));
        }

        guardados.put(id, actualizado.aJson());
        guardarProductos(guardados);
        return respuesta(200, actualizado.aJson());
    }

    private static String obtenerId(Map<String, Object> evento) {
        if (evento != null && evento.get("pathParameters") instanceof Map<?, ?> ruta
                && ruta.get("id") instanceof String id) {
            return id;
        }
        if (evento != null && evento.get("queryStringParameters") instanceof Map<?, ?> consulta
                && consulta.get("id") instanceof String id) {
            return id;
        }
        return null;
    }

    private static boolean esRutaDeProducto(Map<String, Object> evento) {
        return evento != null && evento.get("routeKey") instanceof String ruta
            && ruta.endsWith("/{id}");
    }

    private static String validarId(String id) {
        if (id == null || !id.matches("[0-9]{1,10}")) {
            throw new IllegalArgumentException("El ID debe contener de 1 a 10 dígitos");
        }
        return Long.toString(Long.parseLong(id));
    }

    private static Producto construirProducto(String id, Map<String, String> campos) {
        String nombre = valorTexto(campos.get("nombre"));
        if (nombre == null || nombre.isBlank()) {
            throw new IllegalArgumentException("El nombre no puede estar vacío");
        }

        String valor = campos.get("precio");
        if (valor == null || !valor.matches(NUMERO_JSON)) {
            throw new IllegalArgumentException("El precio debe ser un número mayor o igual a cero");
        }
        BigDecimal precio = new BigDecimal(valor);
        if (precio.signum() < 0) {
            throw new IllegalArgumentException("El precio debe ser un número mayor o igual a cero");
        }
        return new Producto(id, nombre, precio.toString());
    }

    private static Map<String, String> leerCuerpo(Map<String, Object> evento) {
        if (evento != null && evento.get("body") instanceof String cuerpo) {
            return leerObjetoJson(cuerpo);
        }
        throw new IllegalArgumentException("Envía el producto como JSON raw en el body");
    }

    // El producto es un objeto plano con valores escalares.
    private static Map<String, String> leerObjetoJson(String json) {
        String objeto = json.trim();
        if (!objeto.startsWith("{") || !objeto.endsWith("}")) {
            throw new IllegalArgumentException("El cuerpo debe ser un objeto JSON");
        }

        String interior = objeto.substring(1, objeto.length() - 1).trim();
        var campos = new LinkedHashMap<String, String>();
        if (interior.isEmpty()) {
            return campos;
        }

        Matcher campo = CAMPO_JSON.matcher(interior);
        int inicio = 0;
        while (campo.region(inicio, interior.length()).lookingAt()) {
            String clave = valorTexto(campo.group("clave"));
            if (campos.putIfAbsent(clave, campo.group("valor")) != null) {
                throw new IllegalArgumentException("El JSON contiene un campo repetido: " + clave);
            }
            int fin = campo.end();
            if (fin == interior.length()) {
                return campos;
            }
            if (interior.charAt(fin) != ',') {
                break;
            }
            inicio = fin + 1;
        }
        throw new IllegalArgumentException("El cuerpo contiene un JSON inválido");
    }

    private static String valorTexto(String valor) {
        return valor != null && valor.startsWith("\"")
            ? decodificarJson(valor.substring(1, valor.length() - 1))
            : null;
    }

    private static String valorId(Map<String, String> campos) {
        String valor = campos.get("id");
        return valor != null && valor.startsWith("\"") ? valorTexto(valor) : valor;
    }

    private static Map<String, String> leerProductos() throws IOException {
        Path archivo = archivoProductos();
        var productos = new LinkedHashMap<String, String>();
        if (Files.notExists(archivo)) {
            return productos;
        }
        for (String linea : Files.readAllLines(archivo, StandardCharsets.UTF_8)) {
            if (!linea.isBlank()) {
                try {
                    var campos = leerObjetoJson(linea);
                    productos.put(validarId(valorId(campos)), linea.strip());
                } catch (IllegalArgumentException excepcion) {
                    throw new IOException("Registro inválido en el TXT", excepcion);
                }
            }
        }
        return productos;
    }

    private static void guardarProductos(Map<String, String> productos) throws IOException {
        Path archivo = archivoProductos().toAbsolutePath();
        Path carpeta = Files.createDirectories(archivo.getParent());
        Path temporal = Files.createTempFile(carpeta, "productos-", ".tmp");
        try {
            Files.write(temporal, productos.values(), StandardCharsets.UTF_8);
            Files.move(temporal, archivo, StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(temporal);
        }
    }

    private static String obtenerMetodo(Map<String, Object> evento) {
        if (evento == null) {
            return "GET";
        }
        if (evento.get("httpMethod") instanceof String metodoRest) {
            return metodoRest.toUpperCase(Locale.ROOT);
        }
        if (evento.get("requestContext") instanceof Map<?, ?> contexto
                && contexto.get("http") instanceof Map<?, ?> http
                && http.get("method") instanceof String metodoHttp) {
            return metodoHttp.toUpperCase(Locale.ROOT);
        }
        if (evento.get("routeKey") instanceof String routeKey) {
            String[] partes = routeKey.split("\\s+", 2);
            if (partes.length == 2) {
                return partes[0].toUpperCase(Locale.ROOT);
            }
        }
        return "GET";
    }

    private static Path archivoProductos() {
        String rutaConfigurada = System.getenv("PRODUCTOS_FILE_PATH");
        if (rutaConfigurada != null && !rutaConfigurada.isBlank()) {
            return Path.of(rutaConfigurada);
        }
        if (System.getenv("AWS_LAMBDA_FUNCTION_NAME") != null) {
            return Path.of(System.getProperty("java.io.tmpdir"), "productos.txt");
        }
        return Path.of("productos.txt");
    }

    private static String decodificarJson(String valor) {
        StringBuilder resultado = new StringBuilder(valor.length());
        for (int indice = 0; indice < valor.length(); indice++) {
            char caracter = valor.charAt(indice);
            if (caracter != '\\' || indice + 1 >= valor.length()) {
                resultado.append(caracter);
                continue;
            }

            char escapado = valor.charAt(++indice);
            switch (escapado) {
                case '"', '\\', '/' -> resultado.append(escapado);
                case 'b' -> resultado.append('\b');
                case 'f' -> resultado.append('\f');
                case 'n' -> resultado.append('\n');
                case 'r' -> resultado.append('\r');
                case 't' -> resultado.append('\t');
                case 'u' -> {
                    if (indice + 4 >= valor.length()) {
                        throw new IllegalArgumentException("Secuencia Unicode inválida en el JSON");
                    }
                    String hexadecimal = valor.substring(indice + 1, indice + 5);
                    resultado.append((char) Integer.parseInt(hexadecimal, 16));
                    indice += 4;
                }
                default -> throw new IllegalArgumentException(
                    "El cuerpo contiene una secuencia JSON inválida"
                );
            }
        }
        return resultado.toString();
    }

    private static String escaparJson(String valor) {
        StringBuilder resultado = new StringBuilder(valor.length() + 2);
        for (char caracter : valor.toCharArray()) {
            switch (caracter) {
                case '"' -> resultado.append("\\\"");
                case '\\' -> resultado.append("\\\\");
                case '\b' -> resultado.append("\\b");
                case '\f' -> resultado.append("\\f");
                case '\n' -> resultado.append("\\n");
                case '\r' -> resultado.append("\\r");
                case '\t' -> resultado.append("\\t");
                default -> {
                    if (caracter < 0x20) {
                        resultado.append(String.format("\\u%04x", (int) caracter));
                    } else {
                        resultado.append(caracter);
                    }
                }
            }
        }
        return resultado.toString();
    }

    private static String errorJson(String mensaje) {
        return "{\"error\":\"" + escaparJson(mensaje) + "\"}";
    }

    private static Map<String, Object> respuesta(int estado, String cuerpo) {
        // En una integración proxy, body contiene el JSON en forma de texto.
        return Map.of(
            "statusCode", estado,
            "headers", Map.of("Content-Type", "application/json; charset=utf-8"),
            "isBase64Encoded", false,
            "body", cuerpo
        );
    }

    private record Producto(String id, String nombre, String precio) {
        private String aJson() {
            return "{\"id\":" + id + ",\"nombre\":\"" + escaparJson(nombre)
                + "\",\"precio\":" + precio + "}";
        }
    }
}
