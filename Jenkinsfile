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

dt3_quarkusPipeline(
    repoName: 'carbonio-user-management',
    sdk: [module: 'sdk', submoduleChangelogPaths: ['sdk']],
    nativeBuild: [runnerName: 'carbonio-user-management-runner'],
    packaging: [pkgbuildPath: 'package/PKGBUILD'],
    docker: [
        dockerfile: 'docker/Dockerfile',
        imageName: 'carbonio-user-management',
        title: 'Carbonio User Management',
        description: 'Carbonio User Management Service',
    ],
    sonarqube: true,
)
