// SPDX-FileCopyrightText: 2022 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

pipeline {
    agent {
        node {
            label 'openjdk17-agent-v1'
        }
    }
    environment {
        JAVA_OPTS = '-Dfile.encoding=UTF8'
        LC_ALL = 'C.UTF-8'
        jenkins_build = 'true'
    }
    parameters {
        booleanParam defaultValue: false, description: 'Whether to upload the packages in playground repositories', name: 'PLAYGROUND'
        booleanParam defaultValue: false, description: 'Whether to upload the packages in custom repositories', name: 'CUSTOM'
        choice choices: ['rc-jdk17'], description: 'Suffix of the custom repositories (it uploads on the specified repo only if CUSTOM flag is checked)', name: 'SUFFIX_CUSTOM_REPOS'
    }
    options {
        buildDiscarder(logRotator(numToKeepStr: '25'))
        timeout(time: 2, unit: 'HOURS')
        skipDefaultCheckout() // Do the checkout only manually because it is a heavy operation and it can lead to permission problems, conflicts etc
    }
    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }
        stage('Setup') {
            steps {
                withCredentials([file(credentialsId: 'jenkins-maven-settings.xml', variable: 'SETTINGS_PATH')]) {
                    sh "cp ${SETTINGS_PATH} settings-jenkins.xml"
                }
            }
        }
        stage('Build jar') {
            steps {
                sh 'mvn -B --settings settings-jenkins.xml package'
                // having every file within the package directory is great simplification
                sh 'cp boot/target/carbonio-user-management-*-jar-with-dependencies.jar package/carbonio-user-management.jar'
            }
        }
        stage("Tests") {
            parallel {
                stage("UTs") {
                    steps {
                        sh 'mvn -B --settings settings-jenkins.xml verify -P run-unit-tests'
                    }
                }
                stage("ITs") {
                    steps {
                        sh 'mvn -B --settings settings-jenkins.xml verify -P run-integration-tests'
                    }
                }
            }
        }
        stage('Coverage') {
            steps {
                sh 'mvn -B --settings settings-jenkins.xml verify -P generate-jacoco-full-report'
                publishCoverage adapters: [jacocoAdapter('core/target/jacoco-full-report/jacoco.xml')]
            }
        }
        stage('Build deb/rpm') {
            stages {
                // Replace the pkgrel value from SNAPSHOT with the git commit hash to ensure that
                // each merged PR has unique artifacts and to prevent conflicts between them.
                // Note that the pkgrel value will remain as SNAPSHOT in the codebase to avoid
                // conflicts between multiple open PRs
                stage('Snapshot to commit hash') {
                    when {
                        branch 'develop'
                    }
                    steps {
                        sh'''
                            export GIT_COMMIT_SHORT=$(git rev-parse HEAD | head -c 8)
                            sed -i "s/pkgrel=\\"SNAPSHOT\\"/pkgrel=\\"$GIT_COMMIT_SHORT\\"/" ./package/PKGBUILD
                        '''
                    }
                }
                stage('Stash') {
                    steps {
                        stash includes: 'pacur.json,package/**', name: 'binaries'
                    }
                }
                stage('pacur') {
                    parallel {
                        stage('Ubuntu 20.04') {
                            agent {
                                node {
                                    label 'pacur-agent-ubuntu-20.04-v1'
                                }
                            }
                            steps {
                                dir('/tmp/staging'){
                                    unstash 'binaries'
                                }
                                sh 'sudo pacur build ubuntu /tmp/staging/'
                                stash includes: 'artifacts/', name: 'artifacts-deb'
                            }
                            post {
                                always {
                                    archiveArtifacts artifacts: 'artifacts/*.deb', fingerprint: true
                                }
                            }
                        }
                        stage('Rocky 8') {
                            agent {
                                node {
                                    label 'pacur-agent-rocky-8-v1'
                                }
                            }
                            steps {
                                dir('/tmp/staging'){
                                    unstash 'binaries'
                                }
                                sh 'sudo pacur build rocky-8 /tmp/staging/'
                                dir('artifacts/') {
                                    sh 'echo carbonio-user-management* | sed -E "s#(carbonio-user-management-[0-9.]*).*#\\0 \\1.x86_64.rpm#" | xargs sudo mv'
                                }
                                stash includes: 'artifacts/', name: 'artifacts-rpm'
                            }
                            post {
                                always {
                                    archiveArtifacts artifacts: 'artifacts/*.rpm', fingerprint: true
                                }
                            }
                        }
                    }
                }
            }
        }
        stage('Upload to Develop') {
            when {
                branch 'develop'
            }
            steps {
                unstash 'artifacts-deb'
                unstash 'artifacts-rpm'
                script {
                    def server = Artifactory.server 'zextras-artifactory'
                    def buildInfo
                    def uploadSpec

                    buildInfo = Artifactory.newBuildInfo()
                    uploadSpec = '''{
                        "files": [
                            {
                                "pattern": "artifacts/carbonio-user-management*.deb",
                                "target": "ubuntu-devel/pool/",
                                "props": "deb.distribution=bionic;deb.distribution=focal;deb.component=main;deb.architecture=amd64"
                            },
                            {
                                "pattern": "artifacts/(carbonio-user-management)-(*).rpm",
                                "target": "centos8-devel/zextras/{1}/{1}-{2}.rpm",
                                "props": "rpm.metadata.arch=x86_64;rpm.metadata.vendor=zextras"
                            }
                        ]
                    }'''
                    server.upload spec: uploadSpec, buildInfo: buildInfo, failNoOp: false
                }
            }
        }
        stage('Upload to Playground') {
            when {
                anyOf {
                    branch 'playground/*'
                    expression { params.PLAYGROUND == true }
                }
            }
            steps {
                unstash 'artifacts-deb'
                unstash 'artifacts-rpm'
                script {
                    def server = Artifactory.server 'zextras-artifactory'
                    def buildInfo
                    def uploadSpec

                    buildInfo = Artifactory.newBuildInfo()
                    uploadSpec = '''{
                        "files": [
                            {
                                "pattern": "artifacts/carbonio-user-management*.deb",
                                "target": "ubuntu-playground/pool/",
                                "props": "deb.distribution=bionic;deb.distribution=focal;deb.component=main;deb.architecture=amd64"
                            },
                            {
                                "pattern": "artifacts/(carbonio-user-management)-(*).rpm",
                                "target": "centos8-playground/zextras/{1}/{1}-{2}.rpm",
                                "props": "rpm.metadata.arch=x86_64;rpm.metadata.vendor=zextras"
                            }
                        ]
                    }'''
                    server.upload spec: uploadSpec, buildInfo: buildInfo, failNoOp: false
                }
            }
        }
        stage('Upload to Custom') {
            when {
                anyOf {
                    expression { params.CUSTOM == true }
                }
            }
            steps {
                unstash 'artifacts-deb'
                unstash 'artifacts-rpm'
                script {
                    def server = Artifactory.server 'zextras-artifactory'
                    def buildInfo
                    def uploadSpec

                    buildInfo = Artifactory.newBuildInfo()
                    uploadSpec = '''{
                        "files": [
                            {
                                "pattern": "artifacts/carbonio-user-management*.deb",
                                "target": "ubuntu-''' + params.SUFFIX_CUSTOM_REPOS + '''/pool/",
                                "props": "deb.distribution=bionic;deb.distribution=focal;deb.component=main;deb.architecture=amd64"
                            },
                            {
                                "pattern": "artifacts/(carbonio-user-management)-(*).rpm",
                                "target": "centos8-''' + params.SUFFIX_CUSTOM_REPOS + '''/zextras/{1}/{1}-{2}.rpm",
                                "props": "rpm.metadata.arch=x86_64;rpm.metadata.vendor=zextras"
                            }
                        ]
                    }'''
                    server.upload spec: uploadSpec, buildInfo: buildInfo, failNoOp: false
                }
            }
        }
        stage('Upload & Promotion Config') {
            when {
                anyOf {
                    buildingTag()
                }
            }
            steps {
                unstash 'artifacts-deb'
                unstash 'artifacts-rpm'
                script {
                    def server = Artifactory.server 'zextras-artifactory'
                    def buildInfo
                    def uploadSpec
                    def config

                    //ubuntu
                    buildInfo = Artifactory.newBuildInfo()
                    buildInfo.name += '-ubuntu'
                    uploadSpec= '''{
                        "files": [
                            {
                                "pattern": "artifacts/carbonio-user-management*.deb",
                                "target": "ubuntu-rc/pool/",
                                "props": "deb.distribution=bionic;deb.distribution=focal;deb.component=main;deb.architecture=amd64"
                            }
                        ]
                    }'''
                    server.upload spec: uploadSpec, buildInfo: buildInfo, failNoOp: false
                    config = [
                            'buildName'          : buildInfo.name,
                            'buildNumber'        : buildInfo.number,
                            'sourceRepo'         : 'ubuntu-rc',
                            'targetRepo'         : 'ubuntu-release',
                            'comment'            : 'Do not change anything! Just press the button',
                            'status'             : 'Released',
                            'includeDependencies': false,
                            'copy'               : true,
                            'failFast'           : true
                    ]
                    Artifactory.addInteractivePromotion server: server, promotionConfig: config, displayName: 'Ubuntu Promotion to Release'
                    server.publishBuildInfo buildInfo

                    //rocky8
                    buildInfo = Artifactory.newBuildInfo()
                    buildInfo.name += '-centos8'
                    uploadSpec= '''{
                        "files": [
                            {
                                "pattern": "artifacts/(carbonio-user-management)-(*).rpm",
                                "target": "centos8-rc/zextras/{1}/{1}-{2}.rpm",
                                "props": "rpm.metadata.arch=x86_64;rpm.metadata.vendor=zextras"
                            }
                        ]
                    }'''
                    server.upload spec: uploadSpec, buildInfo: buildInfo, failNoOp: false
                    config = [
                            'buildName'          : buildInfo.name,
                            'buildNumber'        : buildInfo.number,
                            'sourceRepo'         : 'centos8-rc',
                            'targetRepo'         : 'centos8-release',
                            'comment'            : 'Do not change anything! Just press the button',
                            'status'             : 'Released',
                            'includeDependencies': false,
                            'copy'               : true,
                            'failFast'           : true
                    ]
                    Artifactory.addInteractivePromotion server: server, promotionConfig: config, displayName: 'RHEL8 Promotion to Release'
                    server.publishBuildInfo buildInfo
                }
            }
        }
    }
}
