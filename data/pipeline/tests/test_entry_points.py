"""`python -m vortex_pipeline` and `python -m vortex_pipeline.bigquery_sandbox` reach their main()."""

import runpy
import sys
import warnings

import pytest


def test_the_pipeline_module_runs_main(monkeypatch):
    monkeypatch.setattr(sys, "argv", ["vortex_pipeline"])
    with pytest.raises(SystemExit, match="vortex_pipeline: missing --movements_input"):
        runpy.run_module("vortex_pipeline", run_name="__main__")


def test_the_sandbox_module_runs_main(monkeypatch, capsys):
    monkeypatch.setattr(sys, "argv", ["bigquery_sandbox"])
    with warnings.catch_warnings():
        # The other tests already imported the module, and runpy warns before running it again
        warnings.simplefilter("ignore", RuntimeWarning)
        with pytest.raises(SystemExit) as exit_info:
            runpy.run_module("vortex_pipeline.bigquery_sandbox", run_name="__main__")
    assert exit_info.value.code == 2
    assert "the following arguments are required" in capsys.readouterr().err
