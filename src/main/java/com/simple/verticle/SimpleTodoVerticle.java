package com.simple.verticle;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
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
        createSampleData();

        // Create a router object.
        Router router = Router.router(vertx);

        router.route("/").handler(StaticHandler.create("assets"));

        router.route("/api/todos*").handler(BodyHandler.create());
        router.get("/api/todos").handler(this::getAll);
        router.post("/api/todos").handler(this::addOne);
        router.put("/api/todos/:id").handler(this::updateOne);
        router.delete("/api/todos/:id").handler(this::deleteOne);

        // Create the HTTP server and pass the "accept" method to the request handler.
        vertx
                .createHttpServer()
                .requestHandler(router::accept)
                .listen(8080, result -> {
                            if (result.succeeded()) {
                                fut.complete();
                            } else {
                                fut.fail(result.cause());
                            }
                        }
                );
    }

    private void addOne(RoutingContext routingContext) {
        JsonObject newTodo = routingContext.getBodyAsJson();
        newTodo.put("_id", TODOS_ID_COUNTER.getAndIncrement());
        mongoClient.insert(MONGO_TODOS_COLLECTION_NAME, newTodo, result -> {
            if (result.succeeded()) {
                routingContext.response()
                        .putHeader("content-type", "application/json; charset=utf-8")
                        .end(Json.encodePrettily(newTodo));
            } else {
                result.cause().printStackTrace();
            }
        });
    }

    private void updateOne(RoutingContext routingContext) {
        String id = routingContext.request().getParam("id");
        JsonObject updatedTodo = routingContext.getBodyAsJson();
        if (id == null || updatedTodo == null) {
            routingContext.response().setStatusCode(400).end();
        } else {
            Integer idAsInteger = Integer.valueOf(id);
            JsonObject updateQuery = new JsonObject().put("_id", idAsInteger);
            JsonObject update = new JsonObject().put("$set", updatedTodo);
            mongoClient.update(MONGO_TODOS_COLLECTION_NAME, updateQuery, update, result -> {
                if (result.succeeded()) {
                    routingContext.response()
                            .putHeader("content-type", "application/json; charset=utf-8")
                            .end(Json.encodePrettily(updatedTodo));
                        } else {
                    result.cause().printStackTrace();
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
            JsonObject query = new JsonObject().put("_id", idAsInteger);
            mongoClient.remove(MONGO_TODOS_COLLECTION_NAME, query, result -> {
                if (result.succeeded()) {
                            routingContext.response().setStatusCode(204).end();
                        } else {
                    result.cause().printStackTrace();
                        }
                    }
            );
        }
    }

    private void getAll(RoutingContext routingContext) {
        JsonObject query = new JsonObject();
        FindOptions options = new FindOptions().setSort(new JsonObject().put("_id", 1));
        mongoClient.findWithOptions(MONGO_TODOS_COLLECTION_NAME, query, options, result -> {
            if (result.succeeded()) {
                        routingContext.response()
                                .putHeader("content-type", "application/json; charset=utf-8")
                                .end(Json.encodePrettily(result.result()));
                    } else {
                result.cause().printStackTrace();
                    }
                }
        );
    }

    private void createSampleData() {
        mongoClient.dropCollection(MONGO_TODOS_COLLECTION_NAME, dropResult -> {
            if (dropResult.succeeded()) {
                Stream.of(
                        createTodo("Check proposal", "GSoC"),
                        createTodo("Accept Artem Voskoboynick as a GSoC 2016 student", "GSoC"),
                        createTodo("Enjoy midterm evaluation results", "GSoC"),
                        createTodo("Enjoy final evaluation results", "GSoC"),
                        createTodo("Have a party!", "GSoC")
                ).forEach(mongoDoc ->
                        mongoClient.insert(MONGO_TODOS_COLLECTION_NAME, mongoDoc, insertResult -> {
                            if (insertResult.failed()) {
                                insertResult.cause().printStackTrace();
                            }
                        })
                );
            } else {
                dropResult.cause().printStackTrace();
            }
        });
    }

    private JsonObject createTodo(String name, String details) {
        return new JsonObject()
                .put("_id", TODOS_ID_COUNTER.getAndIncrement())
                .put("name", name)
                .put("details", details);
    }
}
