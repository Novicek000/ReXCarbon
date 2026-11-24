#pragma once
#include <cstddef>
#include <cstdint>
#include <rex/system/function_dispatcher.h>
size_t RegisterSupplementalIndirectEntries(rex::runtime::FunctionDispatcher*,uint8_t*);
