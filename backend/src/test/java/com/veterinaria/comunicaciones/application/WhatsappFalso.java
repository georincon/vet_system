package com.veterinaria.comunicaciones.application;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.sun.net.httpserver.HttpServer;

/** Servidor HTTP falso de la Cloud API de Meta: registra las peticiones y responde con los codigos programados. */
public final class WhatsappFalso implements AutoCloseable {

    public record Peticion(String metodo, String ruta, String autorizacion, String cuerpo) {
    }

    private final HttpServer server;
    public final List<Peticion> peticiones = new CopyOnWriteArrayList<>();
    private final ConcurrentLinkedQueue<Integer> codigos = new ConcurrentLinkedQueue<>();

    public WhatsappFalso() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        server.createContext("/", ex -> {
            String cuerpo = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            peticiones.add(new Peticion(ex.getRequestMethod(), ex.getRequestURI().getPath(),
                    ex.getRequestHeaders().getFirst("Authorization"), cuerpo));
            Integer codigo = codigos.poll();
            int status = codigo == null ? 200 : codigo;
            String respuesta = status < 300 ? "{\"messaging_product\":\"whatsapp\",\"messages\":[{\"id\":\"wamid.TEST" + peticiones.size() + "\"}]}"
                    : "{\"error\":{\"message\":\"Error simulado " + status + "\",\"code\":" + status + "}}";
            byte[] b = respuesta.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(status, b.length);
            ex.getResponseBody().write(b);
            ex.close();
        });
        server.start();
    }

    /** Respuestas HTTP a entregar en orden a las siguientes peticiones (luego 200). */
    public void programar(int... estados) {
        for (int e : estados) {
            codigos.add(e);
        }
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
