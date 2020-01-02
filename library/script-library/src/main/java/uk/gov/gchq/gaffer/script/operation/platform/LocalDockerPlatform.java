/*
 * Copyright 2019 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package uk.gov.gchq.gaffer.script.operation.platform;

import com.google.common.collect.ImmutableMap;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.exceptions.DockerCertificateException;
import com.spotify.docker.client.exceptions.DockerException;
import com.spotify.docker.client.messages.ContainerConfig;
import com.spotify.docker.client.messages.ContainerCreation;
import com.spotify.docker.client.messages.HostConfig;
import com.spotify.docker.client.messages.PortBinding;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.gov.gchq.gaffer.script.operation.builder.DockerImageBuilder;
import uk.gov.gchq.gaffer.script.operation.container.Container;
import uk.gov.gchq.gaffer.script.operation.container.LocalDockerContainer;
import uk.gov.gchq.gaffer.script.operation.generator.RandomPortGenerator;
import uk.gov.gchq.gaffer.script.operation.image.DockerImage;
import uk.gov.gchq.gaffer.script.operation.image.Image;
import uk.gov.gchq.gaffer.script.operation.util.DockerClientSingleton;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class LocalDockerPlatform implements ImagePlatform {

    private static final Logger LOGGER = LoggerFactory.getLogger(LocalDockerPlatform.class);
    private static final int MAX_TRIES = 100;
    private DockerClient docker = null;
    private String dockerfilePath = "";
    private int port;
    private static final String LOCAL_HOST = "127.0.0.1";

    public static LocalDockerPlatform localDockerPlatform() {
        return new LocalDockerPlatform();
    }

    /**
     * Builds a docker image
     *
     * @param scriptName             the name of the script being run
     * @param scriptParameters       the parameters of the script being run
     * @param pathToBuildFiles       the path to the directory containing the build files
     * @return the docker image
     * @throws InterruptedException             if image build fails
     * @throws DockerException                  if image build fails
     * @throws IOException                      if image build fails
     * @throws DockerCertificateException       if image build fails
     */
    @Override
    public DockerImage buildImage(final String scriptName, final Map<String, Object> scriptParameters, final String pathToBuildFiles) throws IOException, DockerCertificateException, DockerException, InterruptedException {
        final DockerImageBuilder dockerImageBuilder = new DockerImageBuilder();

        // Get the user defined dockerfile or use the default
        dockerImageBuilder.getFiles(pathToBuildFiles, dockerfilePath);

        // Connect to the Docker client. To ensure only one reference to the Docker client and to avoid
        // memory leaks, synchronize this code amongst multiple threads.
        LOGGER.info("Connecting to the Docker client...");
        docker = DockerClientSingleton.getInstance();
        LOGGER.info("Docker is now: {}", docker);
        final DockerImage dockerImage = (DockerImage) dockerImageBuilder.buildImage(scriptName, scriptParameters, pathToBuildFiles);

        // Remove the old images
        cleanImages();

        return dockerImage;
    }

    private void cleanImages() {

        try {
            docker = DockerClientSingleton.getInstance();
        } catch (final DockerCertificateException e) {
            LOGGER.info(e.toString());
            LOGGER.info("Failed to create a connection to the docker client");
        }

        List<com.spotify.docker.client.messages.Image> images = null;
        try {
            images = docker.listImages();
        } catch (final DockerException | InterruptedException e) {
            LOGGER.info(e.toString());
            LOGGER.info("Failed to get a list of docker images");
        }
        try {
            if (images != null) {
                String repoTag = "[<none>:<none>]";
                for (final com.spotify.docker.client.messages.Image image : images) {
                    if (Objects.requireNonNull(image.repoTags()).toString().equals(repoTag)) {
                        docker.removeImage(image.id());
                    }
                }
            }
        } catch (final DockerException | InterruptedException e) {
            LOGGER.info(e.toString());
            LOGGER.info("Could not remove the old images, images still in use.");
        }
    }

    /**
     * Builds a docker image and creates a docker container instance.
     *
     * @param image                  the image to create a container from
     * @return the docker container
     */
    @Override
    public Container createContainer(final Image image) throws DockerException, InterruptedException {

        String containerId = "";
        Exception error = null;
        // Keep trying to create a container and find a free port.
        for (int i = 0; i < MAX_TRIES; i++) {
            if (containerId == null || containerId.equals("")) {
                try {
                    port = RandomPortGenerator.getInstance().generatePort();

                    // Create a container from the image and bind ports
                    final ContainerConfig containerConfig = ContainerConfig.builder()
                            .hostConfig(HostConfig.builder()
                                    .portBindings(ImmutableMap.of("80/tcp",
                                            Collections.singletonList(PortBinding.of(LOCAL_HOST,
                                                    port))))
                                    .build())
                            .image(image.getImageId())
                            .exposedPorts("80/tcp")
                            .cmd("sh", "-c", "while :; do sleep 1; done")
                            .build();
                    final ContainerCreation creation = docker.createContainer(containerConfig);
                    containerId = creation.id();
                    error = null;
                } catch (final DockerException | InterruptedException e) {
                    error = e;
                    RandomPortGenerator.getInstance().releasePort(port);
                }
            }
        }
        // If we still fail to create the container after many tries
        // then print the error message and throw the error.
        if (error != null) {
            LOGGER.error(error.toString());
            LOGGER.error("Failed to create the container");
            DockerClientSingleton.close();
            if (error instanceof DockerException) {
                throw (DockerException) error;
            }
            throw (InterruptedException) error;
        }

        return new LocalDockerContainer(containerId, port);
    }

    /**
     * Starts a docker container
     *
     * @param container             the container
     * @throws DockerException      exception if the container fails to start
     * @throws InterruptedException exception if the container fails to start
     */
    public void startContainer(final Container container) throws InterruptedException, DockerException {
        // Keep trying to start the container
        this.startContainerListener(container.getPort());
        Exception error = null;
        for (int i = 0; i < MAX_TRIES; i++) {
            try {
                LOGGER.info("Starting the Docker container...");
                while (!listenerActive) {
                    Thread.sleep(100);
                }
                docker.startContainer(container.getContainerId());
                while (!containerActive) {
                    Thread.sleep(100);
                }
                error = null;
                break;
            } catch (final DockerException | InterruptedException e) {
                error = e;
            }
        }
        if (error != null) {
            LOGGER.error(error.toString());
            LOGGER.error("Failed to start the container");
            RandomPortGenerator.getInstance().releasePort(container.getPort());
            DockerClientSingleton.close();
            if (error instanceof DockerException) {
                throw (DockerException) error;
            }
            throw (InterruptedException) error;
        }
    }

    boolean listenerActive = false;
    boolean containerActive = false;

    private void startContainerListener(final int port) {
        // Run a HTTP server listener here with a response handler that will then begin the next
        // method.
        HttpServer server = null;
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
        } catch (final IOException e) {
            e.printStackTrace();
        }
        HttpContext context = server.createContext("/");
        context.setHandler(this::containerHTTPHandler);
        server.start();
        listenerActive = true;
    }

    private void containerHTTPHandler(final HttpExchange exchange) throws IOException {
        String response = "Connection Received\n";
        exchange.sendResponseHeaders(200, response.getBytes().length); //response code and length
        OutputStream os = exchange.getResponseBody();
        os.write(response.getBytes());
        os.close();
        System.out.println(response);
        containerActive = true;
    }

    /**
     * Stops and closes a docker container
     *
     * @param container             the container
     */
    public void closeContainer(final Container container) {
        try {
            LOGGER.info("Closing the Docker container...");
            docker.waitContainer(container.getContainerId());
            docker.removeContainer(container.getContainerId());
            // Free the port
            DockerClientSingleton.close();
            RandomPortGenerator.getInstance().releasePort(port);
        } catch (final DockerException | InterruptedException e) {
            LOGGER.info(e.toString());
            LOGGER.info("Failed to stop the container");
        }
    }

    public void runContainer(final Container container, final Iterable inputData) throws DockerException, InterruptedException, IOException {
        try {
            // Start the container
            startContainer(container);
            // Send the data to the container
            container.sendData(inputData);
        } catch (final DockerException | InterruptedException | IOException e) {
            LOGGER.error("Failed to run the container");
            DockerClientSingleton.close();
            RandomPortGenerator.getInstance().releasePort(port);
            throw e;
        }
    }

    private String getDockerfilePath() {
        return dockerfilePath;
    }

    private void setDockerfilePath(final String dockerfilePath) {
        this.dockerfilePath = dockerfilePath;
    }
}
