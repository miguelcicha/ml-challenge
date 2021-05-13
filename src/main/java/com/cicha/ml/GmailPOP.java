/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.cicha.ml;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.json.JsonObject;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import java.util.Properties;
import javax.mail.Authenticator;
import javax.mail.BodyPart;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.internet.MimeMultipart;
import org.jsoup.Jsoup;

/**
 *
 * @author cicha
 */
public class GmailPOP extends AbstractVerticle implements Handler<io.vertx.core.eventbus.Message<JsonObject>> {

    private static final Logger LOGGER = LoggerFactory.getLogger(GmailPOP.class);
    public static final SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
    private String filter;

    @Override
    public void start() {
        this.filter = config().getString("filter");
        vertx.eventBus().consumer("POP.START", this);
    }

    public Future<Void> onSearch() {
        Promise<Void> p = Promise.promise();
        try {
            //Generando archivo de propiedades para java mail desde json
            Properties props = new Properties();
            JsonObject mailConf = config().getJsonObject("mail");
            mailConf.getJsonObject("props").forEach(c -> {
                props.put(c.getKey(), c.getValue());
            });
            Session sesion = Session.getInstance(props, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(mailConf.getString("user"), mailConf.getString("password"));
                }
            });

            Store store = sesion.getStore("pop3s");
            store.connect(mailConf.getJsonObject("props").getString("mail.pop3.host"), mailConf.getString("user"), mailConf.getString("password"));
            Folder folder = store.getFolder("INBOX");
            folder.open(Folder.READ_ONLY);
            Message[] messages = folder.getMessages();
            LOGGER.info("Total a analizar:" + messages.length);
            List<Future> futures = new LinkedList();
            for (Message msg : messages) {
                futures.add(vertx.executeBlocking(r -> {
                    try {
                        if (msg.getSubject().contains(filter) || getTextFromMessage(msg).contains(filter)) {
                            LOGGER.info("Agregado:" + (msg.getSentDate() != null ? sdf.format(msg.getSentDate()) : "") + "  " + msg.getSubject());
                            String subject = msg.getSubject();
                            String fecha = msg.getHeader("Date")[0];
                            String from = String.join(", ", msg.getHeader("From"));
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
                        } else {
                            LOGGER.info(" Omitido: " + (msg.getSentDate() != null ? sdf.format(msg.getSentDate()) : "") + "  " + msg.getSubject());
                        }
                    } catch (Exception e) {
                        r.fail(e);
                    }
                }, false));
            }

            //al completar todos los futuros se culmina la tarea.
            CompositeFuture.join(futures).onSuccess(r -> {
                LOGGER.info("FIN");
                p.complete();
            }).onFailure(ex -> {
                LOGGER.info("ERROR", ex);
                p.fail(ex);
            });

        } catch (Exception e) {
            p.fail(e);
            LOGGER.info(e.getMessage());
        }
        return p.future();
    }

    static private String getTextFromMessage(Message message) throws MessagingException, IOException {
        String result = "";
        if (message.isMimeType("text/plain")) {
            result = message.getContent().toString();
        } else if (message.isMimeType("multipart/*")) {
            MimeMultipart mimeMultipart = (MimeMultipart) message.getContent();
            result = getTextFromMimeMultipart(mimeMultipart);
        }
        return result;
    }

    static private String getTextFromMimeMultipart(MimeMultipart mimeMultipart) throws MessagingException, IOException {
        String result = "";
        int count = mimeMultipart.getCount();
        for (int i = 0; i < count; i++) {
            BodyPart bodyPart = mimeMultipart.getBodyPart(i);
            if (bodyPart.isMimeType("text/plain")) {
                result = result + "\n" + bodyPart.getContent();
                break; // without break same text appears twice in my tests
            } else if (bodyPart.isMimeType("text/html")) {
                String html = (String) bodyPart.getContent();
                result = result + "\n" + Jsoup.parse(html).text();
            } else if (bodyPart.getContent() instanceof MimeMultipart) {
                result = result + getTextFromMimeMultipart((MimeMultipart) bodyPart.getContent());
            }
        }
        return result;
    }

    @Override
    public void handle(io.vertx.core.eventbus.Message<JsonObject> e) {
        switch (e.address()) {
            case "POP.START":
                onSearch();
                break;
        }
    }
}
