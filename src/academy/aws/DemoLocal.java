package academy.aws;

import java.util.LinkedHashMap;
import java.util.Map;

public final class DemoLocal {
    public static void main(String[] args) {
        String id = args.length == 0 ? "40" : args[0];
        if (!id.matches("[0-9]{1,10}")) {
            throw new IllegalArgumentException("Indica un ID libre de 1 a 10 dígitos");
        }

        String variante = args.length < 2 ? "con-prefijo" : args[1];
        boolean conPrefijo = switch (variante) {
            case "con-prefijo" -> true;
            case "sin-prefijo" -> false;
            default -> throw new IllegalArgumentException("Usa con-prefijo o sin-prefijo");
        };
        System.out.println("Rutas de la práctica: " + variante);

        var handler = new CatalogoHandler();
        String producto = """
            {"id":"%s","nombre":"Monitor","precio":180}
            """.formatted(id);
        String cambios = """
            {"nombre":"Monitor HD","precio":195}
            """;

        var crear = evento(conPrefijo, "POST", null, producto);
        probar(handler, "POST para crear", crear, 201);
        probar(handler, "POST duplicado", crear, 409);
        probar(handler, "PUT para actualizar", evento(conPrefijo, "PUT", id, cambios), 200);
        probar(handler, "GET por ID", evento(conPrefijo, "GET", id, null), 200);
        probar(handler, "GET para listar", evento(conPrefijo, "GET", null, null), 200);

        long candidato = 0;
        while (Integer.valueOf(200).equals(handler.handleRequest(
                evento(conPrefijo, "GET", Long.toString(candidato), null)).get("statusCode"))) {
            candidato++;
        }
        probar(handler, "PUT con ID inexistente",
            evento(conPrefijo, "PUT", Long.toString(candidato), cambios), 404);
        probar(handler, "POST con precio negativo", evento(conPrefijo, "POST", null, """
            {"id":"%s","nombre":"Monitor","precio":-1}
            """.formatted(id)), 400);
    }

    private static Map<String, Object> evento(boolean conPrefijo, String metodo, String id, String cuerpo) {
        String ruta = conPrefijo ? "/productos" : "/";
        var evento = new LinkedHashMap<String, Object>();
        evento.put("version", "2.0");
        evento.put("routeKey", metodo + " " + ruta + (conPrefijo && id != null ? "/{id}" : ""));
        evento.put("rawPath", conPrefijo && id != null ? ruta + "/" + id : ruta);
        evento.put("requestContext", Map.of("http", Map.of("method", metodo)));
        if (id != null) {
            evento.put(conPrefijo ? "pathParameters" : "queryStringParameters", Map.of("id", id));
        }
        if (cuerpo != null) {
            evento.put("body", cuerpo);
        }
        return evento;
    }

    private static void probar(CatalogoHandler handler, String etiqueta,
            Map<String, Object> evento, int esperado) {
        var respuesta = handler.handleRequest(evento);
        System.out.printf("%s → HTTP %s%n%s%n%n",
            etiqueta, respuesta.get("statusCode"), respuesta.get("body"));
        if (!Integer.valueOf(esperado).equals(respuesta.get("statusCode"))) {
            throw new IllegalStateException("Se esperaba HTTP " + esperado
                + ". Usa un ID libre para iniciar la demo.");
        }
    }
}
