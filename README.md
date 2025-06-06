# DeSiRe-NG Backend

## Overview

This readme will cover the local project run using Docker.

Requirements:
- Java 17
- Maven v3
- Docker

Backend components:
- Keycloak
  - Authentication manager, manages users, roles and permissions using OAuth2 standards.
  - In the development environment it will run on an embedded database, on deployments it is recommended to use a proper database, see [check supported DBMS here](https://www.keycloak.org/server/db).
- Java backend
  - Spring Boot v3.4 application
- MongoDB database

## Local Setup

### Overview

The following setup guide assumes that the services are installed on a machine that has no DB configured and that is not running other services on the used ports. The default ports are:
- Keycloak: `9090`
- MongoDB: `27017`
- Backend: `8080`

These ports can freely be changed by adapting the Docker and configuration options. E.g., to run Keycloak on a port other than `9090`, change the Docker command to something like:
```bash
docker run -d --name desireng_keycloak -p 9091:8080 […]
```

Similarly, it is possible to use an existing MongoDB service instead of using the proposed Docker setup. If so, change the MongoDB configuration in the backend properties accordingly.

### Keycloak

#### Setup Service

Run Keycloak and import the `desireng` realm. The following commands are using a relative path, so first do a `cd` command to the project base folder.

Windows

```bash
docker run -d --name desireng-keycloak -p 9090:8080 -e KEYCLOAK_ADMIN=admin -e KEYCLOAK_ADMIN_PASSWORD=admin -v %cd%/doc/keycloak:/opt/keycloak/data/import quay.io/keycloak/keycloak:26.2.5 start-dev --import-realm
```

UNIX

```bash
docker run -d --name desireng-keycloak -p 9090:8080 -e KEYCLOAK_ADMIN=admin -e KEYCLOAK_ADMIN_PASSWORD=admin -v ${PWD}/doc/keycloak:/opt/keycloak/data/import quay.io/keycloak/keycloak:26.2.5 start-dev --import-realm
```

Check the server is running at http://localhost:9090/admin using `admin` as username and password.

#### Setup Clients

Change the client secrets for the `desireng` realm by following these steps:
- Login into the master realm.
- Select the `desireng` realm.
- For `desireng` and  `demo-agv` clients, perform the following steps:
  - Open the client details and open the “Credentials” tab.
  - Click on “Regenerate” in “Client Secret” section.
  - Copy the secret key and use it for configuring the client.

The `desireng` client is used by the backend application. Use the generated secret key for the backend configuration (see `KEYCLOAK_SECRET` configuration value).

#### Setup Users

In order to use the application, you will need to select `desireng` realm and create a user with the desired roles:
- Login into the master realm.
- Select the `desireng` realm.
- Open the “Users” page and create a new user.
- Assign an organisation ID and site ID. This can be done or changed later once organisations and sites have been created via the backend API.
- After the user has been created open the user details and navigate to the the “Role mapping” tab.
- Click “Assign roles” and select the “Filter by realm roles” filter.
- For a basic setup, assign all application roles to the demo user (`AGV`, `COLLECTOR`, `OPERATOR`, `ANALYST`, `MANAGER`, `ADMIN`).
- Optionally also assign the `TECHNICAL_ADMIN` and `TECHNICAL_USER` roles for Swagger and Spring Boot Admin access.

### MongoDB

Either configure an existing MongoDB service or setup a Docker based service with the following command:

```bash
docker run --name desireng-mongodb -d -e MONGO_INITDB_ROOT_USERNAME=root -e MONGO_INITDB_ROOT_PASSWORD=root -e MONGO_INITDB_DATABASE=desireng -p 27017:27017 mongo
```

### Java Backend

### Environment Variables

If no Spring profile is activated, default config is:
`/desire/src/main/resources/application-default.properties`

```properties
MONGODB_URI=mongodb://root:root@localhost:27017/desireng?authSource=admin
APPLICATION_BASE_URL=http://localhost:8080
KEYCLOAK_BASE_URL=http://localhost:9090
# Keycloak client service account secret key
KEYCLOAK_SECRET=change-me
TUI_BACKEND_URL=http://localhost:8080/tui
```

#### Running Spring Boot Application

There are several ways to run the backend application locally:

- Running main method: `com.desire.DesireNGApp.main(String[])`
- Using Docker
  - Create the docker image:
    ```bash
    ./build_docker.sh
    ```
  - Run the service:
    ```bash
    docker run --name desireng-backend -d -p 8080:8080 desire:0.0.1-SNAPSHOT
    ```
- Using maven command:
  ```bash
  mvn spring-boot:run
  ```

#### Swagger UI

The Swagger UI can be accessed at http://localhost:8080/swagger-ui.html. To access Swagger UI the `TECHNICAL_USER` role is required.

## Basic Data Setup
In order to use the service some data has to be created in the services. For a basic setup proceed as follows:
- Create a Keycloak user with role `ADMIN`.
- Open the backend Swagger UI (or use any REST client tool) and login with the admin user.
- Create an organisation (see `POST /organisations`). An organisation acts as the base entity for all application data, implementing a multi-tenancy support.
- Create a site for the created organisation (see `POST /organisations/{organizationId}/sites`). A site represents a physical location within an organisation where AGVs can be deployed and measurement campaigns can be performed.
- Create another Keycloak user (or update an existing one) and set the organisation and site IDs as user attributes. For a basic setup, assign all regular user roles (`AGV`, `COLLECTOR`, `OPERATOR`, `ANALYST`, `MANAGER`) to that user to be able to perform all relevant actions.
- With the new/updated user, create/manage measurement campaigns and manage the AGV as needed.

## Check Services Integration Health

In order to check the systems health, the `TECHNICAL_ADMIN` role is required. This application uses Spring Boot Admin CP *(based on Spring Actuator)* to check services integration health status. This can be accessed at http://localhost:8080/admincp.

Once inside the application node on Spring Boot Admin CP, at top right corner there is a *'Heatlh'* module that shows up the status of all integrations, showing whether `UP` or `DOWN` depending on its state.