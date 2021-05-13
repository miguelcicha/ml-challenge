/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.cicha.ml;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.Message;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.json.JsonObject;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.PoolOptions;
import io.vertx.sqlclient.Tuple;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

/**
 *
 * @author cicha
 */
public class DBVerticle extends AbstractVerticle implements Handler<Message<JsonObject>> {
private static final Logger LOGGER = LoggerFactory.getLogger(DBVerticle.class);
    protected PgPool pg;

    @Override
    public void start(Promise<Void> p) {
        JsonObject db = config().getJsonObject("db");

        JsonObject dbConfig = db.getJsonObject("connection");
        PgConnectOptions connectOptions = new PgConnectOptions()
                .setPort(dbConfig.getInteger("port"))
                .setHost(dbConfig.getString("host"))
                .setDatabase(dbConfig.getString("database"))
                .setUser(dbConfig.getString("user"))
                .setPassword(dbConfig.getString("password"));

        PoolOptions poolOptions = new PoolOptions(db.getJsonObject("options"));

        pg = PgPool.pool(vertx, connectOptions, poolOptions);
        init().onSuccess(r -> {
            vertx.eventBus().consumer("db", this);
            p.complete();
        }).onFailure(e -> {
            p.fail(e);
        });
    }

    public Future<Void> insert(String from, String subject, LocalDateTime fecha) {
        Promise<Void> p = Promise.promise();
        pg.preparedQuery("INSERT INTO emails (asunto,emisor,fecha) values ($1,$2,$3)")
                .execute(Tuple.of(subject, from, fecha)).onSuccess(r -> {
            p.complete();
        }).onFailure(ex -> {
            p.fail(ex);
        });
        return p.future();
    }

    public Future<Void> init() {
        Promise<Void> p = Promise.promise();
        pg.query("CREATE TABLE IF NOT EXISTS emails("
                + "asunto VARCHAR (1000),"
                + "emisor VARCHAR (1000),"
                + "fecha timestamp"
                + ")")
                .execute().onSuccess(r -> {
                    p.complete();
                }).onFailure(ex -> {
            p.fail(ex);
        });
        return p.future();
    }

    @Override
    public void handle(Message<JsonObject> e) {
        JsonObject body = e.body();
        
        insert(body.getString("from"), body.getString("subject"), body.getLong("fecha") == null ? null : LocalDateTime.ofInstant(Instant.ofEpochMilli(body.getLong("fecha")), ZoneId.systemDefault())).onSuccess(s -> {
            e.reply(new JsonObject().put("code", 0));
        }).onFailure(ex -> {
            e.reply(new JsonObject().put("code", 1).put("error", ex.getMessage()));
        });
    }
}
