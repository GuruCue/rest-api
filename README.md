# Guru Cue Search &amp; Recommendations REST API

The Search &amp; Recommendations REST API implements the REST API for Guru Cue
Search &amp; Recommendations Engine for:
* providing data about products and consumers,
* providing data about events,
* retrieving recommendations and search results.

## Building the API
The minimum required JDK version to build the API with is 1.8.

Before you start building the API put into the `libs` directory these Guru
Cue Search &amp; Recommendations libraries:
* `database`,
* `data-provider-jdbc`,
* `data-provider-postgresql`.

Perform the build using [gradle](https://gradle.org/). If you don't have it
installed, you can use the gradle wrapper script `gradlew` (Linux and similar)
or `gradlew.bat` (Windows).

## Deploying the API
Before running the API for the first time a PostgreSQL database must be created
and configured. Please refer to the documentation of the
`data-provider-postgresql` library on how to create a database.

Database connection parameters are configured using environment variables:
* `PROVIDER_POSTGRESQL_URL` in the form `jdbc:postgresql://<hostname>/<database>`,
* `PROVIDER_POSTGRESQL_USERNAME` with the username to connect to the database,
* `PROVIDER_POSTGRESQL_PASSWORD` with the password to connect to the database.

The API uses run-time compilation for the blenders subsystem, so Java SE
Development Kit, version 8 or more, must be installed and used to run the API.
Put the blenders' files in source form into the `/opt/GuruCue/blender`
directory. First-level subdirectories must use the `username` field of the
`partner` database table for their names. These directories in turn contain
filter definitions for all blenders of respective partners (blenders for
recommenders stored in the `recommenders` subdirectory, and blenders for
searching stored in the `searchers` subdirectory).

The API must be deployed in a Java EE container such as
[Apache Tomcat](http://tomcat.apache.org/). Simply copy the `war` file that is
the result of the build process, into the Java EE deployment directory.

To be fully functional the API expects recommender engines to be configured
and available. API communicates with recommender engines via RMI. The hostnames
of recommender engines (needed by the RMI subsystem) must be configured in the
database table `recommender`. Each recommender engine can support many
recommenders, which are exposed as a type of built-in filters to the blenders
subsystem. These are configured in the `partner_recommender` database table.

## Using the API
Please refer to the [API documentation](doc/) for details.
