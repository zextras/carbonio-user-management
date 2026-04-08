// SPDX-FileCopyrightText: 2022 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

library(
    identifier: 'jenkins-lib-common@dt3-migration',
    retriever: modernSCM([
        $class: 'GitSCMSource',
        credentialsId: 'jenkins-integration-with-github-account',
        remote: 'git@github.com:zextras/jenkins-lib-common.git',
    ])
)

dt3_pipeline(
    repoName: 'carbonio-user-management',
    sdk: [module: 'sdk'],
    jarBuild: [jarName: 'carbonio-user-management.jar'],
    packaging: [pkgbuildPath: 'package/PKGBUILD', buildFlags: '-ds'],
    docker: [
        [dockerfile: 'docker/Dockerfile',
         imageName: 'carbonio-user-management',
         title: 'Carbonio User Management',
         description: 'Carbonio User Management Service'],
        [dockerfile: 'docker/sidecar/Dockerfile',
         imageName: 'carbonio-user-management-sidecar',
         title: 'Carbonio User Management Sidecar',
         description: 'Carbonio User Management Sidecar Service'],
    ],
    sonarqube: true,
    reuse: [projectType: 'CE'],
)
