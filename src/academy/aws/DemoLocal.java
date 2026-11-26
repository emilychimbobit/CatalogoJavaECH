package academy.aws;

import java.util.Map;

public final class DemoLocal {
    public static void main(String[] args) {
        String id = args.length == 0 ? "40" : args[0];
        if (!id.matches("[0-9]{1,10}")) {
            throw new IllegalArgumentException("Indica un ID libre de 1 a 10 dígitos");
        }

        var handler = new CatalogoHandler();
        var crear = Map.<String, Object>of(
            "routeKey", "POST /productos",
            "body", """
                {"id":"%s","nombre":"Monitor","precio":180}
                """.formatted(id)
        );
        var actualizar = Map.<String, Object>of(
            "routeKey", "PUT /",
            "queryStringParameters", Map.of("id", id),
            "body", """
                {"nombre":"Monitor HD","precio":195}
                """
        );

        probar(handler, "POST para crear", crear, 201);
        probar(handler, "POST duplicado", crear, 409);
        probar(handler, "PUT para actualizar", actualizar, 200);
        probar(handler, "GET por ID", consulta(id), 200);
        probar(handler, "GET para listar", Map.of("routeKey", "GET /productos"), 200);

        long candidato = 0;
        while (Integer.valueOf(200).equals(handler.handleRequest(consulta(Long.toString(candidato))).get("statusCode"))) {
            candidato++;
        }
        probar(handler, "PUT con ID inexistente", Map.of(
            "routeKey", "PUT /productos/{id}",
            "pathParameters", Map.of("id", Long.toString(candidato)),
            "body", actualizar.get("body")
        ), 404);
        probar(handler, "POST con precio negativo", Map.of(
            "routeKey", "POST /productos",
            "body", """
                {"id":"%s","nombre":"Monitor","precio":-1}
                """.formatted(id)
        ), 400);
    }

    private static Map<String, Object> consulta(String id) {
        return Map.of("routeKey", "GET /productos/{id}", "pathParameters", Map.of("id", id));
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
