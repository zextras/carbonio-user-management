// SPDX-FileCopyrightText: 2022 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

library(
    identifier: 'jenkins-dt3-lib@v1.2.1',
    retriever: modernSCM([
        $class: 'GitSCMSource',
        remote: 'git@github.com:zextras/jenkins-dt3-lib.git',
        credentialsId: 'jenkins-integration-with-github-account'
    ])
)

library(
    identifier: 'jenkins-lib-common@1.3.3',
    retriever: modernSCM([
        $class: 'GitSCMSource',
        credentialsId: 'jenkins-integration-with-github-account',
        remote: 'git@github.com:zextras/jenkins-lib-common.git',
    ])
)

properties(defaultPipelineProperties())

pipeline {
    agent {
        node {
            label 'zextras-v1'
        }
    }

    environment {
        JAVA_OPTS = '-Dfile.encoding=UTF8'
        LC_ALL = 'C.UTF-8'
        jenkins_build = 'true'
    }

    options {
        buildDiscarder(logRotator(numToKeepStr: '25'))
        skipDefaultCheckout()
        timeout(time: 2, unit: 'HOURS')
    }

    parameters {
        booleanParam(
            name: 'PREPARE_RELEASE',
            defaultValue: false,
            description: 'Check this to prepare a new release (creates pre-release branch and PR)'
        )
        booleanParam(
            name: 'SKIP_TESTS',
            defaultValue: false,
            description: 'Skip unit tests and integration tests'
        )
        booleanParam(
            name: 'SKIP_CHECKS',
            defaultValue: false,
            description: 'Skip coverage and SonarQube analysis'
        )
    }

    stages {
        stage('Setup') {
            steps {
                checkout scm
                script {
                    gitMetadata()
                }
            }
        }

        stage('Compile and Publish SDK') {
            steps {
                script {
                    def changelist = env.TAG_NAME ? '' : '-SNAPSHOT'
                    container('jdk-21') {
                        sh "mvn -B package -pl sdk -DskipTests -Dchangelist=${changelist}"
                        withCredentials([file(credentialsId: 'jenkins-maven-settings.xml', variable: 'SETTINGS_PATH')]) {
                            sh "mvn -B -s \$SETTINGS_PATH deploy -pl sdk -Dchangelist=${changelist}"
                        }
                    }
                }
            }
        }

        stage('UTs') {
            when {
                expression { params.SKIP_TESTS == false }
            }
            steps {
                container('jdk-21') {
                    sh 'mvn -B verify -pl app -Dskip.unit.tests=false'
                }
            }
        }

        stage('ITs') {
            when {
                expression { params.SKIP_TESTS == false }
            }
            steps {
                container('dind') {
                    withDockerRegistry(credentialsId: 'private-registry', url: 'https://registry.dev.zextras.com') {
                        container('jdk-21') {
                            sh 'mvn -B verify -pl app -Dskip.integration.tests=false'
                        }
                    }
                }
            }
        }

        stage('Coverage') {
            when {
                expression { params.SKIP_CHECKS == false }
            }
            steps {
                container('jdk-21') {
                    sh 'mvn -B verify -pl app -Dskip.unit.tests=false -Dskip.integration.tests=false'
                    recordCoverage(
                        tools: [[parser: 'JACOCO']],
                        sourceCodeRetention: 'MODIFIED'
                    )
                }
            }
        }

        stage('SonarQube analysis') {
            when {
               allOf {
                   expression { params.SKIP_CHECKS == false }
                   anyOf {
                       branch 'devel'
                       expression { env.BRANCH_NAME.contains("PR") }
                   }
               }
            }
            steps {
                container('jdk-21') {
                    withSonarQubeEnv(credentialsId: 'sonarqube-user-token', installationName: 'SonarQube instance') {
                        sh 'mvn -B sonar:sonar'
                    }
                }
            }
        }

        stage('Build native') {
            steps {
                script {
                    def changelist = env.TAG_NAME ? '' : '-SNAPSHOT'
                    container('dind') {
                        sh """
                            apk add --no-cache openjdk21 maven
                            mvn -B package -pl app -am -Dnative \
                                -Dquarkus.native.container-build=true \
                                -DskipTests -Dchangelist=${changelist}
                            cp app/target/*-runner \
                                package/carbonio-user-management-runner
                        """
                    }
                }
            }
        }

        stage('Build deb/rpm') {
            steps {
                script {
                    buildPackages([
                        pkgbuildPath: 'package/PKGBUILD',
                        buildStageConfig: [
                            rockySinglePkg: true,
                            ubuntuSinglePkg: true
                        ]
                    ])
                }
            }
        }

        stage('Upload artifacts') {
            when {
                expression { return uploadStage.shouldUpload() }
            }
            tools {
                jfrog 'jfrog-cli'
            }
            steps {
                uploadStage(
                    packages: yapHelper.resolvePackageNames(),
                    rockySinglePkg: true,
                    ubuntuSinglePkg: true
                )
            }
        }

        stage('Prepare Release') {
            agent {
                node {
                    label 'nodejs-v1'
                }
            }
            when {
                allOf {
                    branch 'devel'
                    expression { params.PREPARE_RELEASE == true }
                    not {
                        expression {
                            return env.GIT_COMMIT_MSG.contains('[skip ci]') ||
                                   env.GIT_COMMIT_MSG.contains('chore(release):')
                        }
                    }
                }
            }
            steps {
                script {
                    container('nodejs-20') {
                        prepareRelease(
                            repoName: 'carbonio-user-management',
                            submoduleChangelogPaths: ['sdk']
                        )
                    }
                }
            }
        }

        stage('Tag for release') {
            when {
                allOf {
                    branch 'devel'
                    expression {
                        return env.GIT_COMMIT_MSG.contains('chore(release):') &&
                               env.GIT_COMMIT_MSG.contains('[skip ci]')
                    }
                }
            }
            steps {
                script {
                    tagRelease()
                }
            }
        }

        stage('Publish docker images') {
            steps {
                dockerStage([
                    dockerfile: 'docker/Dockerfile',
                    imageName: 'carbonio-user-management',
                    ocLabels: [
                        title: 'Carbonio User Management',
                        description: 'Carbonio User Management Service',
                    ]
                ])
            }
        }
    }
}
