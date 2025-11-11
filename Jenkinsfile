// SPDX-FileCopyrightText: 2022 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

library(
    identifier: 'jenkins-dt3-lib@v1.2.0',
    retriever: modernSCM([
        $class: 'GitSCMSource',
        remote: 'git@github.com:zextras/jenkins-dt3-lib.git',
        credentialsId: 'jenkins-integration-with-github-account'
    ])
)

library(
    identifier: 'jenkins-packages-build-library@1.0.4',
    retriever: modernSCM([
        $class: 'GitSCMSource',
        remote: 'git@github.com:zextras/jenkins-packages-build-library.git',
        credentialsId: 'jenkins-integration-with-github-account'
    ])
)

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
        booleanParam defaultValue: false,
            description: 'Whether to upload the packages in playground repositories',
            name: 'PLAYGROUND'
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

    tools {
        jfrog 'jfrog-cli'
    }

    stages {
        stage('Checkout') {
            steps {
                script {
                    checkoutWithMetadata()
                }
            }
        }

        stage('Build jar') {
            steps {
                script {
                    def profile = '-P dev'
                    if (env.TAG_NAME) {
                        profile = '-P prod'
                    }

                    container('jdk-17') {
                        sh """
                            mvn -B package ${profile}
                            cp boot/target/carbonio-user-management-*-jar-with-dependencies.jar \
                                package/carbonio-user-management.jar
                        """
                    }
                }
            }
        }

        stage('UTs') {
            when {
                expression { params.SKIP_TESTS == false }
            }
            steps {
                container('jdk-17') {
                    sh 'mvn -B verify -P run-unit-tests'
                }
            }
        }

        stage('ITs') {
            when {
                expression { params.SKIP_TESTS == false }
            }
            steps {
                container('jdk-17') {
                    sh 'mvn -B verify -P run-integration-tests'
                }
            }
        }

        stage('Coverage') {
            when {
                expression { params.SKIP_CHECKS == false }
            }
            steps {
                container('jdk-17') {
                    sh 'mvn -B verify -P generate-jacoco-full-report'
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
                container('jdk-17') {
                    withSonarQubeEnv(credentialsId: 'sonarqube-user-token', installationName: 'SonarQube instance') {
                        sh 'mvn -B sonar:sonar'
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
            steps {
                uploadStage(
                    packages: yapHelper.getPackageNames(),
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
                            repoName: 'carbonio-user-management'
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

        stage('Build and Publish Docker Image') {
            when {
                not {
                    expression { env.BRANCH_NAME.startsWith('PR-') }
                }
            }
            steps {
                buildAndPublishDockerImage(
                    projectName: 'carbonio-user-management',
                    dockerfile: 'docker/minimal/carbonio-user-management/Dockerfile',
                    imageTitle: 'Carbonio User Management',
                    imageDescription: 'Carbonio User Management'
                )
            }
        }
    }
}