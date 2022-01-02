## Setup

Add this to `.bashrc`.

```bash
function bb-util {
  export BB_CWD=$(pwd); pushd $HOME/slimslenderslacks/bb-util/; bb $@; popd > /dev/null
}

_bb_tasks() {
    COMPREPLY+=(`bb-util tasks |tail -n +3 |cut -f1 -d ' '`)
}
# autocomplete filenames as well
complete -F _bb_tasks bb-util
```


