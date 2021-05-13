# Mercado Libre Challenge Cichanowski Miguel

El aplicativo permite leer una casilla de correos y almacenar en una base de datos ("fecha","from","asunto") todos los correos que contengan "DevOps"

La funcionalidad se desarrollo mediante 2 mecanismos:
* Lectura POP3 de la casilla GMAIL
* Consumo de APIs GMAIL

El resultado del analisis de los correos se visualiza en la consola, en la DB únicamente se cargan las coincidencias.

## Configuración
### Modo POP3

Editar archivo settings.json en config->mail los atributos user,password se debe ingresar el correo electronico a analizar y la contraseña

### Modo API

Cuando ejecute en modo API, se abrira su navegador con un mensaje de "Google no ha verificado esta aplicación", debe dar a opciones avanzadas y luego en permitir. Esto es porque google aún no verificó el client-id generado para éste Challenge


# Ejecución

Se puede ejecutar con docker-compose o standarlone


## Docker

### Prerequisitos:
* docker
* docker-compose

Editar Dockerfile la variable VERTICLE_INIT setear en:
* POP.START
* API.START

El sistema iniciará según el valor configurado en modo POP o API

Para ejecutar con docker, debe dirigirse a la raiz del proyecto y ejecutar:

`docker-compose up --build`

Para limpiar la imagen y volver a ejecutar el proyecto en otro modo, además de editar la variable VERTICLE_INIT debera limpiar el docker para que se vuelva a construir.


## Standarlone

### Prerequisitos:
* Postgres 9 o superior
* JRE 8

### Pasos
1) Ingresar a la carpeta build
2) Editar archivo settings.json, attr config->db con los valores de su motor de base de datos, recuerde crear una base de datos (defecto : ml)
3) Ingresar a la carpeta build y lanzar aplicación con : 

Para modo API: `java -jar ml-1.0.0.jar API.START`
Para modo POP: `java -jar ml-1.0.0.jar POP.START`

