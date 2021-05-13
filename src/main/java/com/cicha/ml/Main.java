/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.cicha.ml;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.json.JsonObject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.stream.Collectors;

/**
 *
 * @author cicha
 */
public class Main {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);
    
    public static void main(String... args) throws IOException {
        JsonObject setting = new JsonObject(Files.readAllLines(new File("settings.json").toPath()).stream().collect(Collectors.joining("\n")));
        Vertx vertx = Vertx.vertx(new VertxOptions(setting.getJsonObject("vertxOptions")));
        vertx.deployVerticle(new DBVerticle(), new DeploymentOptions(setting)).compose(r -> {
            return vertx.deployVerticle(new GmailPOP(), new DeploymentOptions(setting));
        }).compose(r -> {
            return vertx.deployVerticle(new GmailAPI(), new DeploymentOptions(setting));
        }).onSuccess(r -> {
            if (args != null) {
                for (String str : args) {
                    LOGGER.info(str);
                    vertx.eventBus().send(str, "");
                }
            }
        }).onFailure(LOGGER::error);
    }
}
