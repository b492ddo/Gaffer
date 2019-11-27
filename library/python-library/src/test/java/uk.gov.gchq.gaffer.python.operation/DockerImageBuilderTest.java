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
package uk.gov.gchq.gaffer.python.operation;

import com.spotify.docker.client.DefaultDockerClient;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.exceptions.DockerCertificateException;
import org.junit.Assert;
import org.junit.Test;

import java.nio.file.FileSystems;
import java.nio.file.Path;

public class DockerImageBuilderTest {

    @Test
    public void shouldBuildImage() {
        // Given
        DockerClient docker = null;
        final String currentWorkingDirectory = FileSystems.getDefault().getPath(".").toAbsolutePath().toString();
        final String directoryPath = currentWorkingDirectory.concat(PythonTestConstants.CURRENT_WORKING_DIRECTORY);
        Path pathAbsolutePythonRepo = DockerFileUtils.getPathAbsolutePythonRepo(directoryPath, PythonTestConstants.REPO_NAME);
        DockerImageBuilder imageBuilder = new DockerImageBuilder();

        final GitScriptProvider scriptProvider = new GitScriptProvider();
        scriptProvider.getScripts(pathAbsolutePythonRepo.toString(), PythonTestConstants.REPO_URI);

        try {
            docker = DefaultDockerClient.fromEnv().build();
        } catch (DockerCertificateException e) {
            e.printStackTrace();
        }

        // When
        imageBuilder.getFiles(directoryPath, "");
        Image returnedImage = imageBuilder.buildImage("script1", null, docker,
                    directoryPath);

        // Then
        Assert.assertNotNull(returnedImage);
    }
}
