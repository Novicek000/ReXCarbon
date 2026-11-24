#pragma once
#include <memory>
#include <string_view>
#include <rex/filesystem/device.h>

namespace rexlauncher {
// Duplicates the descriptor. Never resolves/reopens a provider-owned path.
std::unique_ptr<rex::filesystem::Device> make_saf_disc(std::string_view mount, int fd);
}
