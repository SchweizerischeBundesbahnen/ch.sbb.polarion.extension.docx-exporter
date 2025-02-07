docker container stop polarion-2410-arm64
mvn clean install -P install-to-local-polarion
docker container start polarion-2410-arm64

# mvn clean package -P tests-with-weasyprint-docker

#  -DskipTests=true
#sudo /opt/polarion/bin/polarion.init stop
#mvn clean install -P polarion2304,install-to-local-polarion
#sudo rm -fr /opt/polarion/data/workspace/.config/*
#sudo rm -f /opt/polarion/data/logs/main/*
#sudo /opt/polarion/bin/polarion.init start

# docker exec -it polarion-2310-arm64 bash
