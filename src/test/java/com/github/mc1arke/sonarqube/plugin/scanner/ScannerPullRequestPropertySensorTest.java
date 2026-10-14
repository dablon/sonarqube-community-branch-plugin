/*
 * Copyright (C) 2020-2024 Michael Clarke
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 *
 */
package com.github.mc1arke.sonarqube.plugin.scanner;

import com.github.mc1arke.sonarqube.plugin.ce.pullrequest.gitlab.GitlabMergeRequestDecorator;
import org.junit.jupiter.api.Test;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.utils.System2;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SonarQube 26.10 elimino los fixtures de test del batch filesystem
 * ({@code org.sonar.api.batch.fs.internal.TestInputFileBuilder} y
 * {@code DefaultInputFile} ya no existen en la distribucion), asi que este test se apoya
 * en un {@link SensorContext} mockeado. El sensor solo publica propiedades de contexto,
 * de modo que no hace falta tocar el filesystem para comprobarlo, y asi se cubren
 * tambien las propiedades de sistema, que antes no se verificaban.
 */
class ScannerPullRequestPropertySensorTest {

    private final System2 system2 = mock();
    private final SensorContext sensorContext = mock();
    private final ScannerPullRequestPropertySensor sensor = new ScannerPullRequestPropertySensor(system2);

    @Test
    void shouldPublishBothGitlabPropertiesFromEnvironmentVariables() {
        when(system2.envVariable("GITLAB_CI")).thenReturn("true");
        when(system2.envVariable("CI_MERGE_REQUEST_PROJECT_URL")).thenReturn("https://gitlab.example.com/proj");
        when(system2.envVariable("CI_PIPELINE_ID")).thenReturn("12345");

        sensor.execute(sensorContext);

        assertThat(capturedKeys()).containsExactlyInAnyOrder(
                GitlabMergeRequestDecorator.PULLREQUEST_GITLAB_PROJECT_URL,
                GitlabMergeRequestDecorator.PULLREQUEST_GITLAB_PIPELINE_ID);
    }

    @Test
    void shouldPublishOnlyThePropertiesPresentInTheEnvironment() {
        when(system2.envVariable("GITLAB_CI")).thenReturn("true");
        when(system2.envVariable("CI_MERGE_REQUEST_PROJECT_URL")).thenReturn("https://gitlab.example.com/proj");

        sensor.execute(sensorContext);

        assertThat(capturedKeys()).containsExactly(
                GitlabMergeRequestDecorator.PULLREQUEST_GITLAB_PROJECT_URL);
    }

    @Test
    void shouldPublishNothingOutsideGitlabCi() {
        when(system2.envVariable("GITLAB_CI")).thenReturn("false");
        when(system2.envVariable("CI_MERGE_REQUEST_PROJECT_URL")).thenReturn("https://gitlab.example.com/proj");
        when(system2.envVariable("CI_PIPELINE_ID")).thenReturn("12345");

        sensor.execute(sensorContext);

        assertThat(capturedKeys()).isEmpty();
    }

    @Test
    void shouldPublishPropertiesFromSystemProperties() {
        when(system2.envVariable("GITLAB_CI")).thenReturn("false");
        when(system2.property(GitlabMergeRequestDecorator.PULLREQUEST_GITLAB_PROJECT_URL))
                .thenReturn("https://gitlab.example.com/proj");
        when(system2.property(GitlabMergeRequestDecorator.PULLREQUEST_GITLAB_PIPELINE_ID)).thenReturn("999");

        sensor.execute(sensorContext);

        assertThat(capturedKeys()).containsExactlyInAnyOrder(
                GitlabMergeRequestDecorator.PULLREQUEST_GITLAB_PROJECT_URL,
                GitlabMergeRequestDecorator.PULLREQUEST_GITLAB_PIPELINE_ID);
    }

    private List<String> capturedKeys() {
        org.mockito.ArgumentCaptor<String> captor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(sensorContext, org.mockito.Mockito.atLeast(0))
                .addContextProperty(captor.capture(), anyString());
        return captor.getAllValues();
    }

}
