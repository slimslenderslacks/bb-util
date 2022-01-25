## Setup

Add this to `.bashrc`.

```bash
function bb-util {
  export BB_CWD=$(pwd); pushd $HOME/slimslenderslacks/bb-util/; bb cli "$@"; popd > /dev/null
}

_bb_tasks() {
    COMPREPLY=( $(compgen -W "$(bb-util slim/completions)" -- ${COMP_WORDS[COMP_CWORD]}) );
}
# autocomplete filenames as well
complete -F _bb_tasks bb-util
```


