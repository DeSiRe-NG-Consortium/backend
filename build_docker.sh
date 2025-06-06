#!/bin/bash

# Copyright 2023–2925 Nuromedia GmbH
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

function close() {
    read -p "Press ENTER key to exit"
    exit $1
}

echo -e "MAVEN: building project…\n"

mvn clean install 
# -DskipTests=true

if [[ $? -ne 0 ]]
then
    echo -e "\nERROR: Maven build\n"
    close 1
fi

echo -e "\nDOCKER: Building project in folder: $(basename "$PWD")\n"

MAVEN_ARTIFACT_ID=$(mvn -q -Dexec.executable=echo -Dexec.args='${project.artifactId}' --non-recursive exec:exec | sed -r 's/([[:alnum:]]+)/\1/' | head -n 1)

MAVEN_PROJECT_VERSION=$(mvn -q -Dexec.executable=echo -Dexec.args='${project.version}' --non-recursive exec:exec | sed -r 's/([[:alnum:]]+)/\1/' | head -n 1)

if [[ -z $MAVEN_ARTIFACT_ID || -z $MAVEN_PROJECT_VERSION ]]
then
    echo -e "\nERROR: Could not determine Maven app info\n"
    close 1
fi

echo -e "\nApp name: $MAVEN_ARTIFACT_ID at version: $MAVEN_PROJECT_VERSION.\n"

echo -e "\nDOCKER: Building docker container…\n"

docker build --tag=$MAVEN_ARTIFACT_ID:$MAVEN_PROJECT_VERSION --build-arg JAR_FILE="$MAVEN_ARTIFACT_ID-$MAVEN_PROJECT_VERSION.jar" .

if [[ $? -ne 0 ]]
then
    echo -e "\nERROR: building Docker container\n"
    close 1
fi

GIT_COMMIT_ID=$(cat src/main/resources/git.properties | sed -r 's/git.commit.id.abbrev=(.+)/\1/;t;d')
GIT_BRANCH=$(cat src/main/resources/git.properties | sed -r 's/git.branch=(.+)/\1/;t;d')

if [[ -z $GIT_COMMIT_ID || -z $GIT_BRANCH ]]
then
    echo -e "\nError: Could not determine Git info\n"
    close 1
fi

echo -e "\nGIT: Using commit $GIT_COMMIT_ID in branch $GIT_BRANCH\n"

DOCKER_IMAGE="$MAVEN_ARTIFACT_ID:$MAVEN_PROJECT_VERSION"

echo -e "DOCKER: Exporting image $DOCKER_IMAGE to target folder\n"

DOCKER_FILE="target/$MAVEN_ARTIFACT_ID-$MAVEN_PROJECT_VERSION-$(date '+%Y%m%d-%H%M%S')-$GIT_COMMIT_ID-docker.tar.xz"

docker save $DOCKER_IMAGE | xz -z -F xz -9 -T 0 > $DOCKER_FILE

if [[ $? -ne 0 ]]
then
    echo -e "\nERROR: Docker export error\n"
    close 1
fi

echo -e "\e[1;32mSUCCESS: Exported file: $DOCKER_FILE\n"

close 0
