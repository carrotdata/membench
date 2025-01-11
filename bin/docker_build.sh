: '
  This script is used to build a docker image for memcarrot for signal or multiplatform.
'

HOME_DIR=$(dirname "$(realpath "$0")")
cd $HOME_DIR || exit

show_help() {
    echo "Usage: docker_build.sh [--tag TAG] [--help]"
    echo
    echo "   -t, --tag  <...>      Specify the tag of membench version 0.12 or 0.13 ..."
    echo "   -h, --help            Display this help message"
}

# Initialize variables
tag=""

# Parse short options with getopts
while getopts ":h:t:" opt; do
    case ${opt} in
        h )
            show_help
            exit 0
            ;;
        t )
            tag=$OPTARG
            ;;
        \? )
            echo "Invalid option: $OPTARG" 1>&2
            show_help
            exit 1
            ;;
        : )
            echo "Invalid option: $OPTARG requires an argument" 1>&2
            show_help
            exit 1
            ;;
    esac
done
shift $((OPTIND -1))

# Manually parse long options
while [[ $# -gt 0 ]]; do
    case "$1" in
        --help)
            show_help
            exit 0
            ;;
        --tag)
            if [[ -n $2 ]]; then
                tag=$2
                shift 2
            else
                echo "Error: --tag requires an argument." 1>&2
                show_help
                exit 1
            fi
            ;;
        *)
            echo "Invalid option: $1" 1>&2
            show_help
            exit 1
            ;;
    esac
done

if [ -z "$tag" ]; then
    echo "Please provide tag. Run . ./docker_github_env.sh --tag <tag>"
    show_help
    exit 1
fi

JAR_FILENAME="membench-${tag}-jar-with-dependencies.jar"
file_name="${HOME}/.m2/repository/com/carrotdata/membench/${tag}/${JAR_FILENAME}"
if [ ! -f "${file_name}" ]; then
    echo "membench build not found: ${file_name}"
    exit 1
fi

echo "Make ${tag} for membench version '$file_name'"

cd ..

if [ ! -d "target" ]; then
  mkdir target
fi
cp -p ${file_name} ./target

docker_image_name="carrotdata/membench"
arch="arm64"
docker_arch="aarch64"
latest_tag=latest-${arch}

docker_cmd=$(docker build --no-cache --build-arg JAR_FILENAME=${JAR_FILENAME} --build-arg ARCH="${docker_arch}" -t ${docker_image_name}:"${latest_tag}" .)

echo
echo "Building docker image for membench version ${tag} with the following command:"
echo ${docker_cmd}
echo
${docker_cmd}

#docker scout quickview
