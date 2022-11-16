# example: sh ./run_integration_test.sh <local|dev|uat|prod>

# create containers
cd ../docker || exit
sh ./run_docker.sh "$1"

# waiting the containers
printf 'Waiting for the service'
attempt_counter=0
max_attempts=50
until $(curl --output /dev/null --silent --head --fail http://localhost:8080/info); do
    if [ ${attempt_counter} -eq ${max_attempts} ];then
      echo "Max attempts reached"
      exit 1
    fi

    printf '.'
    attempt_counter=$((attempt_counter+1))
    sleep 6
done
echo 'Service Started'

# run integration tests
cd ../integration-test/src || exit
yarn install
yarn test
