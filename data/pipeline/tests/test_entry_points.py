"""`python -m vortex_pipeline` and its command-line modules reach their main()."""

import runpy
import sys
import warnings

import pytest


def test_the_pipeline_module_runs_main(monkeypatch):
    monkeypatch.setattr(sys, "argv", ["vortex_pipeline"])
    with pytest.raises(SystemExit, match="vortex_pipeline: missing --movements_input"):
        runpy.run_module("vortex_pipeline", run_name="__main__")


@pytest.mark.parametrize("module", ["bigquery_sandbox", "dataflow_check"])
def test_the_command_line_modules_run_main(monkeypatch, capsys, module):
    monkeypatch.setattr(sys, "argv", [module])
    with warnings.catch_warnings():
        # The other tests already imported the module, and runpy warns before running it again
        warnings.simplefilter("ignore", RuntimeWarning)
        with pytest.raises(SystemExit) as exit_info:
            runpy.run_module(f"vortex_pipeline.{module}", run_name="__main__")
    assert exit_info.value.code == 2
    assert "the following arguments are required" in capsys.readouterr().err
