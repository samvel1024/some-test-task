# sudo apt install httpie jq

HOST="${HOST:-localhost}"
PORT="${PORT:-8080}"
URL="http://${HOST}:${PORT}"

function create-dummies() {

  for i in {1..100} ; do
      http "$URL/publish" content="MSG$i"
  done

}

function get-between(){
  from=$1
  to=$2
  http "$URL/getByTime?start=$from&end=$to"
}

function get-all() {
  get-between "2000-01-01T00:00" "2200-01-01T00:00"
}


function example() {
  create-dummies
  OUTPUT=$(get-all | jq -rc ".[] | .created")

  echo "Messages between [2, 12]"
  get-between "$(echo "$OUTPUT" | sed -n '2p')" "$(echo "$OUTPUT" | sed -n '12p')" \
   | jq -rc ".[] | .content"

  echo "Messages between [80, 92]"
  get-between "$(echo "$OUTPUT" | sed -n '80p')" "$(echo "$OUTPUT" | sed -n '92p')" \
   | jq -rc ".[] | .content"

}

function run() {
  gradle build && docker-compose up -d
}