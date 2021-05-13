package com.cicha.ml;

// Copyright 2018 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
// [START gmail_quickstart]
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.Base64;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.GmailScopes;
import com.google.api.services.gmail.model.ListMessagesResponse;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePart;
import com.google.api.services.gmail.model.MessagePartHeader;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.json.JsonObject;
import java.io.FileInputStream;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class GmailAPI extends AbstractVerticle implements Handler<io.vertx.core.eventbus.Message<JsonObject>> {

    private static final Logger LOGGER = LoggerFactory.getLogger(GmailAPI.class);
    private static final String APPLICATION_NAME = "mercadolibre";
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final String TOKENS_DIRECTORY_PATH = "tokens";

    /**
     * Global instance of the scopes required by this quickstart. If modifying
     * these scopes, delete your previously saved tokens/ folder.
     */
    private static final List<String> SCOPES = Collections.singletonList(GmailScopes.GMAIL_READONLY);
    private static final String CREDENTIALS_FILE_PATH = "credentials.json";

    private String filter;

    @Override
    public void start() {
        this.filter = config().getString("filter");
        vertx.eventBus().consumer("API.START", this);
    }

    /**
     * Creates an authorized Credential object.
     *
     * @param HTTP_TRANSPORT The network HTTP Transport.
     * @return An authorized Credential object.
     * @throws IOException If the credentials.json file cannot be found.
     */
    private Credential getCredentials(final NetHttpTransport HTTP_TRANSPORT) throws IOException {
        // Load client secrets.
        InputStream in = new FileInputStream(CREDENTIALS_FILE_PATH);
        if (in == null) {
            throw new FileNotFoundException("Resource not found: " + CREDENTIALS_FILE_PATH);
        }
        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

        // Build flow and trigger user authorization request.
        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
                .setDataStoreFactory(new FileDataStoreFactory(new java.io.File(TOKENS_DIRECTORY_PATH)))
                .setAccessType("offline")
                .build();
        LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(8888).build();
        return new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");
    }

    public Future<Void> onSearch() {
        Promise<Void> p = Promise.promise();
        try {
            final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
            Gmail service = new Gmail.Builder(HTTP_TRANSPORT, JSON_FACTORY, getCredentials(HTTP_TRANSPORT))
                    .setApplicationName(APPLICATION_NAME)
                    .build();

            ListMessagesResponse listResponse = service.users().messages().list("me")
                    .setQ(filter)
                    .execute();
            List<Message> messages = listResponse.getMessages();
            if (messages == null || messages.isEmpty()) {
                LOGGER.info("No found.");
                p.complete();
            } else {
                LOGGER.info("Total Coincidencias:" + messages.size());
                List<Future> futures = new LinkedList();
                for (Message m : messages) {
                    Future f = vertx.executeBlocking(r -> {
                        try {
                            Message msg = service.users().messages().get("me", m.getId()).setFormat("Full").execute();
                            String subject = null, fecha = null, from = null;
                            for (MessagePartHeader mph : msg.getPayload().getHeaders()) {
                                switch (mph.getName()) {
                                    case "Date":
                                        fecha = mph.getValue();
                                        break;
                                    case "Subject":
                                        subject = mph.getValue();
                                        break;
                                    case "From":
                                        from = mph.getValue();
                                        break;
                                }
                                if (fecha != null && subject != null && from != null) {
                                    break;
                                }
                            }
//                            String body = getContent(msg);
                            LOGGER.info("Agregado: " + fecha + " " + subject);
                            Long millis = null;
                            try {
                                millis = fecha == null ? null : LocalDateTime.parse(fecha, DateTimeFormatter.RFC_1123_DATE_TIME).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
                            } catch (Exception e) {
                            }
                            vertx.eventBus().request("db", new JsonObject().put("subject", subject).put("from", from).put("fecha", millis)).onSuccess(a -> {
                                if (((JsonObject) a.body()).getInteger("code") == 0) {
                                    r.complete();
                                } else {
                                    r.fail(((JsonObject) a.body()).getString("error"));
                                }
                            }).onFailure(r::fail);
                        } catch (Exception e) {
                            r.fail(e);
                        }
                    }, false);
                    futures.add(f);
                }
                CompositeFuture.join(futures).onSuccess(r -> {
                    LOGGER.info("FIN");
                    p.complete();
                }).onFailure(ex -> {
                    LOGGER.info("ERROR", ex);
                    p.fail(ex);
                });
            }
        } catch (Exception e) {
            p.fail(e);
        }
        return p.future();
    }

    public String getContent(Message message) {
        StringBuilder stringBuilder = new StringBuilder();
        try {
            getPlainTextFromMessageParts(message.getPayload().getParts(), stringBuilder);
            byte[] bodyBytes = Base64.decodeBase64(stringBuilder.toString());
            String text = new String(bodyBytes, StandardCharsets.UTF_8);
            return text;
        } catch (Exception e) {
            LOGGER.info(e.toString());
            return message.getSnippet();
        }
    }

    private void getPlainTextFromMessageParts(List<MessagePart> messageParts, StringBuilder stringBuilder) {
        for (MessagePart messagePart : messageParts) {
            if (messagePart.getMimeType().equals("text/plain")) {
                stringBuilder.append(messagePart.getBody().getData());
            }
            if (messagePart.getParts() != null) {
                getPlainTextFromMessageParts(messagePart.getParts(), stringBuilder);
            }
        }
    }

    @Override
    public void handle(io.vertx.core.eventbus.Message<JsonObject> e) {
        switch (e.address()) {
            case "API.START":
                onSearch();
                break;
        }
    }
}
