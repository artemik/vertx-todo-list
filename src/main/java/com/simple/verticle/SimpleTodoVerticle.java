package com.simple.verticle;

import com.simple.entity.TodoItem;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.StaticHandler;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author Artem on 26.03.2016
 * @version $Id: $
 */
public class SimpleTodoVerticle extends AbstractVerticle {
    private Map<Integer, TodoItem> products = new LinkedHashMap<>();

    /**
     * This method is called when the verticle is deployed. It creates a HTTP server and registers a simple request
     * handler.
     * <p>
     * Notice the `listen` method. It passes a lambda checking the port binding result. When the HTTP server has been
     * bound on the port, it call the `complete` method to inform that the starting has completed. Else it reports the
     * error.
     *
     * @param fut the future
     */
    @Override
    public void start(Future<Void> fut) {
        createSomeData();

        // Create a router object.
        Router router = Router.router(vertx);

        // Bind "/" to our hello message.
        router.route("/").handler(routingContext -> {
            HttpServerResponse response = routingContext.response();
            response
                    .putHeader("content-type", "text/html")
                    .end("<h1>Hello from my first Vert.x 3 application</h1>");
        });

        router.route("/assets/*").handler(StaticHandler.create("assets"));

        router.get("/api/todos").handler(this::getAll);
        router.route("/api/todos*").handler(BodyHandler.create());
        router.post("/api/todos").handler(this::addOne);
        router.get("/api/todos/:id").handler(this::getOne);
        router.put("/api/todos/:id").handler(this::updateOne);
        router.delete("/api/todos/:id").handler(this::deleteOne);


        // Create the HTTP server and pass the "accept" method to the request handler.
        vertx
                .createHttpServer()
                .requestHandler(router::accept)
                .listen(
                        // Retrieve the port from the configuration,
                        // default to 8080.
                        config().getInteger("http.port", 8080),
                        result -> {
                            if (result.succeeded()) {
                                fut.complete();
                            } else {
                                fut.fail(result.cause());
                            }
                        }
                );
    }

    private void addOne(RoutingContext routingContext) {
        // Read the request's content and create an instance of TodoItem.
        final TodoItem todoItem = Json.decodeValue(routingContext.getBodyAsString(),
                TodoItem.class);
        // Add it to the backend map
        products.put(todoItem.getId(), todoItem);

        // Return the created todoItem as JSON
        routingContext.response()
                .setStatusCode(201)
                .putHeader("content-type", "application/json; charset=utf-8")
                .end(Json.encodePrettily(todoItem));
    }

    private void getOne(RoutingContext routingContext) {
        final String id = routingContext.request().getParam("id");
        if (id == null) {
            routingContext.response().setStatusCode(400).end();
        } else {
            final Integer idAsInteger = Integer.valueOf(id);
            TodoItem todoItem = products.get(idAsInteger);
            if (todoItem == null) {
                routingContext.response().setStatusCode(404).end();
            } else {
                routingContext.response()
                        .putHeader("content-type", "application/json; charset=utf-8")
                        .end(Json.encodePrettily(todoItem));
            }
        }
    }

    private void updateOne(RoutingContext routingContext) {
        final String id = routingContext.request().getParam("id");
        JsonObject json = routingContext.getBodyAsJson();
        if (id == null || json == null) {
            routingContext.response().setStatusCode(400).end();
        } else {
            final Integer idAsInteger = Integer.valueOf(id);
            TodoItem todoItem = products.get(idAsInteger);
            if (todoItem == null) {
                routingContext.response().setStatusCode(404).end();
            } else {
                todoItem.setName(json.getString("name"));
                todoItem.setOrigin(json.getString("origin"));
                routingContext.response()
                        .putHeader("content-type", "application/json; charset=utf-8")
                        .end(Json.encodePrettily(todoItem));
            }
        }
    }

    private void deleteOne(RoutingContext routingContext) {
        String id = routingContext.request().getParam("id");
        if (id == null) {
            routingContext.response().setStatusCode(400).end();
        } else {
            Integer idAsInteger = Integer.valueOf(id);
            products.remove(idAsInteger);
        }
        routingContext.response().setStatusCode(204).end();
    }

    private void getAll(RoutingContext routingContext) {
        // Write the HTTP response
        // The response is in JSON using the utf-8 encoding
        // We returns the list of bottles
        routingContext.response()
                .putHeader("content-type", "application/json; charset=utf-8")
                .end(Json.encodePrettily(products.values()));
    }

    private void createSomeData() {
        TodoItem first = new TodoItem("Check proposal.", "GSoC");
        TodoItem second = new TodoItem("Accept Artem Voskoboynick as a GSoC 2016 student.", "GSoC");
        TodoItem third = new TodoItem("Enjoy midterm evaluation results.", "GSoC");
        TodoItem fourth = new TodoItem("Enjoy final evaluation results.", "GSoC");
        TodoItem fifth = new TodoItem("Have a party!", "GSoC");
        products.put(first.getId(), first);
        products.put(second.getId(), second);
        products.put(third.getId(), third);
        products.put(fourth.getId(), fourth);
        products.put(fifth.getId(), fifth);
    }
}
