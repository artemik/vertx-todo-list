package com.simple.verticle;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.FindOptions;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.StaticHandler;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * @author Artem on 26.03.2016
 * @version $Id: $
 */
public class SimpleTodoVerticle extends AbstractVerticle {
    private static final String MONGO_DB_NAME = "todos-db";
    private static final String MONGO_TODOS_COLLECTION_NAME = "todos";
    private static final AtomicInteger TODOS_ID_COUNTER = new AtomicInteger();

    private MongoClient mongoClient;

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
        mongoClient = MongoClient.createShared(
                vertx, new JsonObject().put("db_name", MONGO_DB_NAME));
        createSomeData();

        // Create a router object.
        Router router = Router.router(vertx);

        // Bind "/" to our hello message.
        router.route("/").handler(routingContext -> {
            HttpServerResponse response = routingContext.response();
            response
                    .putHeader("content-type", "text/html")
                    .end("<h1>Hello from Simple Todo List application</h1>");
        });

        router.route("/assets/*").handler(StaticHandler.create("assets"));

        router.route("/api/todos*").handler(BodyHandler.create());
        router.get("/api/todos").handler(this::getAll);
        router.post("/api/todos").handler(this::addOne);
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
        JsonObject json = routingContext.getBodyAsJson();

        JsonObject doc = createTodoDocument(json.getString("name"), json.getString("details"));
        mongoClient.insert(MONGO_TODOS_COLLECTION_NAME, doc, addRes -> {
            if (addRes.succeeded()) {
                mongoClient.find(
                        MONGO_TODOS_COLLECTION_NAME,
                        new JsonObject()
                                .put("id", doc.getValue("id")),
                        findRes -> {
                            if (findRes.succeeded()) {
                                routingContext.response()
                                        .putHeader("content-type", "application/json; charset=utf-8")
                                        .end(Json.encodePrettily(findRes.result()));
                            } else {
                                findRes.cause().printStackTrace();
                            }
                        }
                );
            } else {
                addRes.cause().printStackTrace();
            }
        });
    }

    private void updateOne(RoutingContext routingContext) {
        String id = routingContext.request().getParam("id");
        JsonObject json = routingContext.getBodyAsJson();
        if (id == null || json == null) {
            routingContext.response().setStatusCode(400).end();
        } else {
            Integer idAsInteger = Integer.valueOf(id);
            mongoClient.update(
                    MONGO_TODOS_COLLECTION_NAME,
                    new JsonObject()
                            .put("id", idAsInteger),
                    new JsonObject().put("$set",
                            new JsonObject()
                                    .put("name", json.getString("name"))
                                    .put("details", json.getString("details"))),
                    updateRes -> {
                        if (updateRes.succeeded()) {
                            mongoClient.find(
                                    MONGO_TODOS_COLLECTION_NAME,
                                    new JsonObject()
                                            .put("id", idAsInteger),
                                    findRes -> {
                                        if (findRes.succeeded()) {
                                            routingContext.response()
                                                    .putHeader("content-type", "application/json; charset=utf-8")
                                                    .end(Json.encodePrettily(findRes.result()));
                                        } else {
                                            findRes.cause().printStackTrace();
                                        }
                                    }
                            );
                        } else {
                            updateRes.cause().printStackTrace();
                        }
                    }
            );
        }
    }

    private void deleteOne(RoutingContext routingContext) {
        String id = routingContext.request().getParam("id");
        if (id == null) {
            routingContext.response().setStatusCode(400).end();
        } else {
            Integer idAsInteger = Integer.valueOf(id);
            mongoClient.remove(
                    MONGO_TODOS_COLLECTION_NAME,
                    new JsonObject().put("id", idAsInteger),
                    res -> {
                        if (res.succeeded()) {
                            routingContext.response().setStatusCode(204).end();
                        } else {
                            res.cause().printStackTrace();
                        }
                    }
            );
        }
    }

    private void getAll(RoutingContext routingContext) {
        // Write the HTTP response
        // The response is in JSON using the utf-8 encoding
        // We return the list of todos
        mongoClient.findWithOptions(
                MONGO_TODOS_COLLECTION_NAME,
                new JsonObject(),
                new FindOptions().setSort(new JsonObject().put("id", 1)),
                res -> {
                    if (res.succeeded()) {
                        routingContext.response()
                                .putHeader("content-type", "application/json; charset=utf-8")
                                .end(Json.encodePrettily(res.result()));
                    } else {
                        res.cause().printStackTrace();
                    }
                }
        );
    }

    private void createSomeData() {
        mongoClient.dropCollection(MONGO_TODOS_COLLECTION_NAME, dropRes -> {
            if (dropRes.succeeded()) {
                Stream
                        .of(
                                createTodoDocument("Check proposal", "GSoC"),
                                createTodoDocument("Accept Artem Voskoboynick as a GSoC 2016 student", "GSoC"),
                                createTodoDocument("Enjoy midterm evaluation results", "GSoC"),
                                createTodoDocument("Enjoy final evaluation results", "GSoC"),
                                createTodoDocument("Have a party!", "GSoC")
                        )
                        .forEach(doc ->
                                mongoClient.insert(MONGO_TODOS_COLLECTION_NAME, doc, insertRes -> {
                                    if (insertRes.failed()) {
                                        insertRes.cause().printStackTrace();
                                    }
                                })
                        );
            } else {
                dropRes.cause().printStackTrace();
            }
        });
    }

    private JsonObject createTodoDocument(String name, String details) {
        return new JsonObject()
                .put("id", TODOS_ID_COUNTER.getAndIncrement())
                .put("name", name)
                .put("details", details);
    }
}
