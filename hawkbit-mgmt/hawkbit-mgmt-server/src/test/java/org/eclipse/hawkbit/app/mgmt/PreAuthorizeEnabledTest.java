/**
 * Copyright (c) 2023 Bosch.IO GmbH and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.app.mgmt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.util.HashMap;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.qameta.allure.Description;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import org.eclipse.hawkbit.im.authentication.SpPermission;
import org.eclipse.hawkbit.im.authentication.SpRole;
import org.eclipse.hawkbit.repository.test.util.WithUser;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

@Feature("Integration Test - Security")
@Story("PreAuthorized enabled")
class PreAuthorizeEnabledTest extends AbstractSecurityTest {

    @Test
    @Description("Tests whether request fail if a role is forbidden for the user")
    @WithUser(authorities = { SpPermission.READ_TARGET }, autoCreateTenant = false)
    void failIfNoRole() throws Exception {
        mvc.perform(get("/rest/v1/distributionsets")).andExpect(result ->
                assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value()));
    }

    @Test
    @Description("Tests whether request succeed if a role is granted for the user")
    @WithUser(authorities = { SpPermission.READ_REPOSITORY }, autoCreateTenant = false)
    void successIfHasRole() throws Exception {
        mvc.perform(get("/rest/v1/distributionsets")).andExpect(result ->
                assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value()));
    }

    @Test
    @Description("Tests whether request succeed if a role is granted for the user")
    @WithUser(authorities = { SpRole.TENANT_ADMIN }, autoCreateTenant = false)
    void successIfHasTenantAdminRole() throws Exception {
        mvc.perform(get("/rest/v1/distributionsets")).andExpect(result ->
                assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value()));
    }

    @Test
    @Description("Tests whether read tenant config request fail if a tenant config (or read read) is not granted for the user")
    @WithUser(authorities = { SpPermission.READ_TARGET }, autoCreateTenant = false)
    void onlyDSIfNoTenantConfig() throws Exception {
        mvc.perform(get("/rest/v1/system/configs")).andExpect(result -> {
            // returns default DS type because of READ_TARGET
            assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
            assertThat(new ObjectMapper().reader().readValue(result.getResponse().getContentAsString(), HashMap.class))
                    .hasSize(1);
        });
    }

    @Test
    @Description("Tests whether read tenant config request succeed if a tenant config (not read explicitly) is granted for the user")
    @WithUser(authorities = { SpPermission.TENANT_CONFIGURATION }, autoCreateTenant = false)
    void successIfHasTenantConfig() throws Exception {
        mvc.perform(get("/rest/v1/system/configs")).andExpect(result ->
                assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value()));
    }
}