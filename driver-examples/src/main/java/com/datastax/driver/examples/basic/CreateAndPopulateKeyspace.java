/*
 * Copyright (C) 2012-2017 DataStax Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.datastax.driver.examples.basic;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.ProtocolVersion;
import io.netty.channel.Channel;
import io.netty.channel.socket.nio.NioSocketChannel;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.SocketChannel;

import static java.lang.Thread.sleep;

/**
 * Creates a keyspace and tables, and loads some data into them.
 * <p/>
 * Preconditions:
 * - a Cassandra cluster is running and accessible through the contacts points identified by CONTACT_POINTS and PORT.
 * <p/>
 * Side effects:
 * - creates a new keyspace "simplex" in the cluster. If a keyspace with this name already exists, it will be reused;
 * - creates two tables "simplex.songs" and "simplex.playlists". If they exist already, they will be reused;
 * - inserts a row in each table.
 *
 * @see <a href="http://datastax.github.io/java-driver/manual/">Java driver online manual</a>
 */
public class CreateAndPopulateKeyspace {

    static String[] CONTACT_POINTS = {"10.0.0.23"};
    //static String[] CONTACT_POINTS = {"127.0.0.1"};
    static int PORT = 9161;
    static int REDIRECT_PORT_NUM = 10000;

    public static void main(String[] args) {

        CreateAndPopulateKeyspace client = new CreateAndPopulateKeyspace();

        client.connect(CONTACT_POINTS, PORT);

        Thread thread = new Thread(new Watchdog());
        thread.start();


        while (true) {
            try {

                while (true) {
                    client.createSchema();
                    sleep(1000);
                }
                //client.loadData();
                //client.querySchema();

            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                client.close();
            }
        }
    }

    static public class Watchdog implements Runnable {
        public void run() {
            System.out.println("Watchdog running");
            String address = "localhost";
            SocketChannel socketChannel;
            try {
                socketChannel = SocketChannel.open(new InetSocketAddress(address, REDIRECT_PORT_NUM));
                Channel channel = new NioSocketChannel(socketChannel);
                //channel.connect(new InetSocketAddress("localhost", REDIRECT_PORT_NUM));
                while(true) {
                    System.out.println("writeAndFlush to "+address);
                    channel.writeAndFlush("test");
                    sleep(1000);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private Cluster cluster;

    private Session session;

    /**
     * Initiates a connection to the cluster
     * specified by the given contact point.
     *
     * @param contactPoints the contact points to use.
     * @param port          the port to use.
     */
    public void connect(String[] contactPoints, int port) {

        cluster = Cluster.builder()
                .addContactPoints(contactPoints).withPort(port)
                .withProtocolVersion(ProtocolVersion.V2)
                .build();

        System.out.printf("Connected to cluster: %s%n", cluster.getMetadata().getClusterName());

        session = cluster.connect();
    }

    /**
     * Creates the schema (keyspace) and tables
     * for this example.
     */
    public void createSchema() {

        System.out.println("Create new schema.");
        session.execute("CREATE KEYSPACE IF NOT EXISTS simplex WITH replication " +
                "= {'class':'SimpleStrategy', 'replication_factor':3};");

        session.execute(
                "CREATE TABLE IF NOT EXISTS simplex.songs (" +
                        "id uuid PRIMARY KEY," +
                        "title text," +
                        "album text," +
                        "artist text," +
                        "tags set<text>," +
                        "data blob" +
                        ");");

        session.execute(
                "CREATE TABLE IF NOT EXISTS simplex.playlists (" +
                        "id uuid," +
                        "title text," +
                        "album text, " +
                        "artist text," +
                        "song_id uuid," +
                        "PRIMARY KEY (id, title, album, artist)" +
                        ");");
    }

    /**
     * Inserts data into the tables.
     */
    public void loadData() {

        session.execute(
                "INSERT INTO simplex.songs (id, title, album, artist, tags) " +
                        "VALUES (" +
                        "756716f7-2e54-4715-9f00-91dcbea6cf50," +
                        "'La Petite Tonkinoise'," +
                        "'Bye Bye Blackbird'," +
                        "'Joséphine Baker'," +
                        "{'jazz', '2013'})" +
                        ";");

        session.execute(
                "INSERT INTO simplex.playlists (id, song_id, title, album, artist) " +
                        "VALUES (" +
                        "2cc9ccb7-6221-4ccb-8387-f22b6a1b354d," +
                        "756716f7-2e54-4715-9f00-91dcbea6cf50," +
                        "'La Petite Tonkinoise'," +
                        "'Bye Bye Blackbird'," +
                        "'Joséphine Baker'" +
                        ");");
    }

    /**
     * Queries and displays data.
     */
    public void querySchema() {

        ResultSet results = session.execute(
                "SELECT * FROM simplex.playlists " +
                        "WHERE id = 2cc9ccb7-6221-4ccb-8387-f22b6a1b354d;");

        System.out.printf("%-30s\t%-20s\t%-20s%n", "title", "album", "artist");
        System.out.println("-------------------------------+-----------------------+--------------------");

        for (Row row : results) {

            System.out.printf("%-30s\t%-20s\t%-20s%n",
                    row.getString("title"),
                    row.getString("album"),
                    row.getString("artist"));

        }

    }

    /**
     * Closes the session and the cluster.
     */
    public void close() {
        session.close();
        cluster.close();
    }

}
