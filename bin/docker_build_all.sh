: '
  This script is build wrapper for building all the docker images for memcarrot.
'

HOME_DIR=$(dirname "$(realpath "$0")")
cd $HOME_DIR || exit

 ./docker_build.sh -t 0.15.2
