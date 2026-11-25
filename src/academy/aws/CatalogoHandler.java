package academy.aws;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CatalogoHandler {
    private static final Pattern CAMPO_TEXTO = Pattern.compile(
        "\"(?<campo>[^\"]+)\"\\s*:\\s*\"(?<valor>(?:\\\\.|[^\"\\\\])*)\""
    );
    private static final Pattern CAMPO_NUMERO = Pattern.compile(
        "\"(?<campo>[^\"]+)\"\\s*:\\s*(?<valor>-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?)"
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
        String metodo = obtenerMetodo(evento);
        if ("POST".equals(metodo)) {
            return agregarProducto(evento);
        }
        if (!"GET".equals(metodo)) {
            return respuesta(405, """
                {"error":"Método no permitido"}
                """.strip());
        }

        String id = null;

        if (evento != null) {
            if (evento.get("pathParameters") instanceof Map<?, ?> parametrosRuta
                    && parametrosRuta.get("id") instanceof String idRuta) {
                id = idRuta;
            }

            if (id == null
                    && evento.get("queryStringParameters") instanceof Map<?, ?> parametrosConsulta
                    && parametrosConsulta.get("id") instanceof String idConsulta) {
                id = idConsulta;
            }
        }

        if (id == null || !id.matches("[0-9]{1,10}")) {
            return respuesta(400, """
                {"error":"El ID debe contener de 1 a 10 dígitos"}
                """.strip());
        }

        String producto = PRODUCTOS.get(id);
        if (producto == null) {
            try {
                producto = buscarProductoGuardado(id);
            } catch (IOException excepcion) {
                return respuesta(500, errorJson(
                    "No se pudo consultar el archivo de productos: " + excepcion.getMessage()
                ));
            }
        }
        if (producto == null) {
            return respuesta(404, """
                {"error":"Producto no encontrado"}
                """.strip());
        }

        return respuesta(200, producto);
    }

    private static synchronized Map<String, Object> agregarProducto(Map<String, Object> evento) {
        String cuerpo;
        try {
            cuerpo = obtenerCuerpo(evento);
        } catch (IllegalArgumentException excepcion) {
            return respuesta(400, """
                {"error":"El cuerpo Base64 no es válido"}
                """.strip());
        }
        if (cuerpo == null || cuerpo.isBlank()) {
            return respuesta(400, """
                {"error":"El cuerpo debe contener un producto en formato JSON"}
                """.strip());
        }

        try {
            Producto nuevo = parsearProducto(cuerpo);
            Path archivo = archivoProductos();

            if (PRODUCTOS.containsKey(nuevo.id())
                    || buscarProductoGuardado(nuevo.id()) != null) {
                return respuesta(409, """
                    {"error":"Ya existe un producto con ese ID"}
                    """.strip());
            }

            Files.createDirectories(archivo.toAbsolutePath().getParent());
            Files.writeString(
                archivo,
                nuevo.aJson() + System.lineSeparator(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
            );
            return respuesta(201, nuevo.aJson());
        } catch (IllegalArgumentException excepcion) {
            return respuesta(400, errorJson(excepcion.getMessage()));
        } catch (IOException excepcion) {
            return respuesta(500, errorJson(
                "No se pudo guardar el producto en el archivo: " + excepcion.getMessage()
            ));
        }
    }

    private static String obtenerCuerpo(Map<String, Object> evento) {
        if (evento == null || !(evento.get("body") instanceof String cuerpo)) {
            return null;
        }
        if (Boolean.TRUE.equals(evento.get("isBase64Encoded"))) {
            return new String(Base64.getDecoder().decode(cuerpo), StandardCharsets.UTF_8);
        }
        return cuerpo;
    }

    private static Producto parsearProducto(String cuerpo) {
        String id = valorTexto(cuerpo, "id");
        if (id == null) {
            id = valorNumero(cuerpo, "id");
        }

        String nombre = valorTexto(cuerpo, "nombre");
        String precio = valorNumero(cuerpo, "precio");

        if (id == null || !id.matches("[0-9]{1,10}")) {
            throw new IllegalArgumentException(
                "El ID debe ser numérico y contener de 1 a 10 dígitos"
            );
        }
        if (nombre == null || nombre.isBlank()) {
            throw new IllegalArgumentException("El nombre no puede estar vacío");
        }
        if (precio == null || new BigDecimal(precio).compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("El precio debe ser un número mayor o igual a cero");
        }

        return new Producto(id, decodificarJson(nombre), new BigDecimal(precio).toPlainString());
    }

    private static String buscarProductoGuardado(String id) throws IOException {
        Path archivo = archivoProductos();
        if (!Files.exists(archivo)) {
            return null;
        }

        List<String> lineas = Files.readAllLines(archivo, StandardCharsets.UTF_8);
        for (int indice = lineas.size() - 1; indice >= 0; indice--) {
            String linea = lineas.get(indice).trim();
            if (!linea.isEmpty() && id.equals(valorNumero(linea, "id"))) {
                return linea;
            }
        }
        return null;
    }

    private static String valorTexto(String json, String campo) {
        Matcher matcher = CAMPO_TEXTO.matcher(json);
        while (matcher.find()) {
            if (campo.equals(decodificarJson(matcher.group("campo")))) {
                return matcher.group("valor");
            }
        }
        return null;
    }

    private static String valorNumero(String json, String campo) {
        Matcher matcher = CAMPO_NUMERO.matcher(json);
        while (matcher.find()) {
            if (campo.equals(decodificarJson(matcher.group("campo")))) {
                return matcher.group("valor");
            }
        }
        return null;
    }

    private static String obtenerMetodo(Map<String, Object> evento) {
        if (evento == null) {
            return "GET";
        }
        if (evento.get("httpMethod") instanceof String metodoRest) {
            return metodoRest.toUpperCase();
        }
        if (evento.get("requestContext") instanceof Map<?, ?> contexto
                && contexto.get("http") instanceof Map<?, ?> http
                && http.get("method") instanceof String metodoHttp) {
            return metodoHttp.toUpperCase();
        }
        if (evento.get("routeKey") instanceof String routeKey) {
            String[] partes = routeKey.split("\\s+", 2);
            if (partes.length == 2) {
                return partes[0].toUpperCase();
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
