#!/usr/bin/env python3
"""Isolate unmodified SDK InputSystem routing from platform backend factories.

The test uses the real constructor, enumeration, assignment and GetState /
GetCapabilities bodies. Only the unused default SDL backend factory and the
vibration method depending on global CVar registration are excluded.
"""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
sdk = root/'ports/nfsmw-android/sdk'
source = (sdk/'src/input/input_system.cpp').read_text()
start = source.index('namespace rex::input {')
end = source.index('std::unique_ptr<InputSystem> CreateDefaultInputSystem(')
core = source[start:end]
vibration = core.index('X_RESULT InputSystem::SetState(')
keystroke = core.index('X_RESULT InputSystem::GetKeystroke(',vibration)
core = core[:vibration]+core[keystroke:]
includes = '''#include <chrono>
#include <algorithm>
#include <cmath>
#include <rex/dbg.h>
#include <rex/input/device_assignment.h>
#include <rex/input/input_driver.h>
#include <rex/input/input_system.h>
#include <rex/input/state_merge.h>
'''
output = root/'build/input-test-core.cpp'
output.parent.mkdir(exist_ok=True)
output.write_text(includes+core+'\n} // namespace rex::input\n')
print(output)
